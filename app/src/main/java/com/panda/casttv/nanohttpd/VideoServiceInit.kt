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
                        margin: 0; 
                        padding: 0; 
                        background-color: #000; 
                        color: white; 
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                    }
                    #status { 
                        position: absolute; 
                        top: 10px; 
                        left: 10px; 
                        font-size: 12px; 
                        color: gray; 
                        z-index: 10;
                    }
                    #media-container { 
                        width: 100%; 
                        height: 100%; 
                        display: flex; 
                        justify-content: center; 
                        align-items: center; 
                    }
                    video, img { 
                        max-width: 100%; 
                        max-height: 100vh; 
                        object-fit: contain; 
                    }
                    #debug-text {
                        display: none; /* Change to block to debug JSON */
                        position: absolute;
                        bottom: 10px;
                        color: yellow;
                    }
                </style>
            </head>
            <body>
                <div id="status">Disconnected</div>
                <div id="media-container">
                    <!-- Media will be injected here -->
                    <h2 id="placeholder">Waiting for media...</h2>
                </div>
                <div id="debug-text"></div>

                <script>
                    var ws;
                    function connect() {
                        // Connect to the WebSocket created in Kotlin
                        ws = new WebSocket("ws://${host}:${server?.port}");
                        
                        ws.onopen = function() {
                            document.getElementById("status").innerText = "Connected";
                            document.getElementById("status").style.color = "lime";
                        };

                        ws.onmessage = function(event) {
                            console.log("Received: " + event.data);
                            // Optional: show raw data for debugging
                            // document.getElementById("debug-text").innerText = event.data;

                            try {
                                var payload = JSON.parse(event.data);
                                handleMediaCommand(payload);
                            } catch (e) {
                                console.error("Invalid JSON", e);
                            }           
                        };

                        ws.onclose = function() {
                            document.getElementById("status").innerText = "Disconnected";
                            document.getElementById("status").style.color = "red";
                            // Try to reconnect after 2 seconds
                             setTimeout(connect, 2000);
                        };
                    }

                    function handleMediaCommand(data) {
                        if (data.command !== "LOAD_MEDIA") return;

                        var container = document.getElementById("media-container");
                        container.innerHTML = ""; // Clear current content

                        if (data.mimeType.startsWith("video")) {
                            // Create Video Element
                            var video = document.createElement("video");
                            video.src = data.url;
                            video.controls = true;
                            video.autoplay = true;
                            // 'muted' is often required for autoplay to work on modern browsers/Android WebViews
                            // unless there has been user interaction.
                            // video.muted = true; 
                            container.appendChild(video);
                            
                            var playPromise = video.play();
                            if (playPromise !== undefined) {
                                playPromise.catch(error => {
                                    console.log("Autoplay prevented, adding muted.");
                                    video.muted = true;
                                    video.play();
                                });
                            }

                        } else if (data.mimeType.startsWith("image")) {
                            // Create Image Element
                            var img = document.createElement("img");
                            img.src = data.url;
                            container.appendChild(img);
                        } else {
                            // Unsupported format
                            container.innerHTML = "<h2>Unsupported media type: " + data.mimeType + "</h2>";
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