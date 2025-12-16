package com.panda.casttv.nanohttpd

import android.content.Context
import android.net.Uri
import android.util.Log
import com.panda.casttv.MainActivity.Companion.findAvailablePort
import fi.iki.elonen.NanoHTTPD

class VideoServiceInit(
    val context: Context,
    val host: String,
    val port: Int,
) : NanoHTTPD(host, port) {

    val videoURL: String
        get() = "http://$host:$port/"

    var server: VideoWebSocket? = null

    val TAG: String
        get() = this::class.java.simpleName

    init {
        Log.d(TAG, "init: $videoURL")
    }


    override fun serve(session: IHTTPSession?): Response? {
        Log.d(TAG, "serve: ")

        if (server == null || server?.isAlive != true) {
            server?.stop()
            val portAvailable = findAvailablePort()
            server = VideoWebSocket(context, host, portAvailable)
            try {
                server?.start(0)
                Log.d(TAG, "startServerSocket: $portAvailable")
            } catch (e: Exception) {
                e.printStackTrace()
                server = null
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Cast Receiver</title>
                <style>
                    body { 
                        font-family: sans-serif; 
                        margin: 0; padding: 0; 
                        background-color: #000; 
                        color: white; 
                        display: flex; flex-direction: column;
                        align-items: center; justify-content: center;
                        min-height: 100vh;
                        overflow: hidden;
                    }
                    #status { 
                        position: absolute; top: 10px; left: 10px; 
                        font-size: 12px; color: gray; z-index: 10;
                    }
                    #media-container { 
                        width: 100%; height: 100%; 
                        display: flex; justify-content: center; align-items: center; 
                    }
                    video, img { 
                        max-width: 100%; max-height: 100vh; 
                        object-fit: contain; 
                    }
                </style>
            </head>
            <body>
                <div id="status">Disconnected</div>
                <div id="media-container">
                    <h2 id="placeholder">Ready to Cast</h2>
                </div>

                <script>
                    var ws;
                    var currentMediaElement = null; // Store reference to current video/img

                    function connect() {
                        ws = new WebSocket("ws://${host}:${server?.port}");
                        
                        ws.onopen = function() {
                            document.getElementById("status").innerText = "Connected";
                            document.getElementById("status").style.color = "lime";
                        };

                        ws.onmessage = function(event) {
                            console.log("Received: " + event.data);
                            try {
                                var msg = JSON.parse(event.data);
                                processMessage(msg);
                            } catch (e) {
                                console.error("JSON Error", e);
                            }           
                        };

                        ws.onclose = function() {
                            document.getElementById("status").innerText = "Disconnected";
                            document.getElementById("status").style.color = "red";
                            setTimeout(connect, 2000);
                        };
                    }

                    function processMessage(data) {
                        // 1. Handle New Media
                        if (data.command === "LOAD_MEDIA") {
                            loadMedia(data);
                        } 
                        // 2. Handle Controls (Play/Pause/Seek)
                        else if (data.command === "CONTROL") {
                            controlMedia(data);
                        }
                    }

                    function loadMedia(data) {
                        var container = document.getElementById("media-container");
                        
                        // Clean up existing media events
                        if (currentMediaElement) {
                            currentMediaElement.pause && currentMediaElement.pause();
                            currentMediaElement.src = "";
                            currentMediaElement = null;
                        }
                        container.innerHTML = "";

                        if (data.mimeType.startsWith("video")) {
                            var video = document.createElement("video");
                            video.src = data.url;
                            video.controls = false; // Hide default controls if you want full remote control
                            video.autoplay = true;
                            
                            // Attach Event Listeners to send status back to Android
                            attachVideoListeners(video);
                            
                            container.appendChild(video);
                            currentMediaElement = video;
                            
                            var p = video.play();
                            if (p !== undefined) {
                                p.catch(e => { video.muted = true; video.play(); });
                            }

                        } else if (data.mimeType.startsWith("image")) {
                            var img = document.createElement("img");
                            img.src = data.url;
                            container.appendChild(img);
                            currentMediaElement = img;
                        }
                    }

                    function controlMedia(data) {
                        if (!currentMediaElement || currentMediaElement.tagName !== "VIDEO") return;

                        switch (data.action) {
                            case "PLAY":
                                currentMediaElement.play();
                                break;
                            case "PAUSE":
                                currentMediaElement.pause();
                                break;
                            case "SEEK":
                                // Expecting data.position in seconds
                                if (data.position !== undefined) {
                                    currentMediaElement.currentTime = data.position;
                                }
                                break;
                        }
                    }

                    function attachVideoListeners(video) {
                        // Notify Android when video starts playing
                        video.onplay = function() {
                            sendToAndroid({ event: "STATE_CHANGED", state: "PLAYING" });
                        };

                        // Notify Android when video is paused
                        video.onpause = function() {
                            sendToAndroid({ event: "STATE_CHANGED", state: "PAUSED" });
                        };

                        // Notify Android about progress (every ~250ms)
                        // Useful for updating Seekbar on Android
                        var lastUpdate = 0;
                        video.ontimeupdate = function() {
                            var now = Date.now();
                            if (now - lastUpdate > 500) { // Limit updates to 2x per second
                                sendToAndroid({ 
                                    event: "PROGRESS", 
                                    currentTime: video.currentTime, 
                                    duration: video.duration 
                                });
                                lastUpdate = now;
                            }
                        };

                        // Notify when video ends
                        video.onended = function() {
                            sendToAndroid({ event: "STATE_CHANGED", state: "ENDED" });
                        };
                    }

                    function sendToAndroid(jsonObj) {
                        if (ws && ws.readyState === WebSocket.OPEN) {
                            ws.send(JSON.stringify(jsonObj));
                        }
                    }

                    connect();
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(html)
    }
}