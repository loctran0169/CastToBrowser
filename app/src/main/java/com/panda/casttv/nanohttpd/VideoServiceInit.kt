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

    var server: VideoWebSocket? = null

    val TAG: String
        get() = this::class.java.simpleName

    init {
        Log.d(TAG, "init: $host:$port")
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
                <title>Android NanoHttpd</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; text-align: center; }
                    #status { font-weight: bold; color: gray; }
                    #data { font-size: 24px; color: #00796B; margin-top: 20px; border: 1px solid #ccc; padding: 20px; }
                </style>
            </head>
            <body>
                <h1>Live Data from Android</h1>
                <div id="status">Disconnected</div>
                <div id="data">Waiting for updates...</div>

                <script>
                    var ws;
                    function connect() {
                        // Connect to the WebSocket at the root path
                        ws = new WebSocket("ws://${host}:${server?.port}");
                        console.log("ws://${host}:${server?.port}");
                        ws.onopen = function() {
                            document.getElementById("status").innerText = "Connected";
                            document.getElementById("status").style.color = "green";
                        };

                        ws.onmessage = function(event) {
                            // Update the UI when data arrives
                            console.log(event.data);
                            document.getElementById("data").innerText = event.data;                      
                        };

                        ws.onclose = function() {
                            document.getElementById("status").innerText = "Disconnected";
                            document.getElementById("status").style.color = "red";
                            // Try to reconnect after 2 seconds
                             setTimeout(connect, 2000);
                        };
                    }
                    connect();
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(html)
    }
}