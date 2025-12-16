package com.panda.casttv.nanohttpd

import android.content.Context
import android.net.Uri
import android.util.Log
import com.panda.casttv.MainActivity
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

class VideoWebSocket(
    val context: Context,
    val host: String,
    val port: Int,
) : NanoWSD(host, port) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val TAG: String
            get() = "VideoWebSocket"
    }

    init {
        Log.d(TAG, "init: $host:$port")
    }

    private val sockets = arrayListOf<WebSocket>()

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        Log.d(TAG, "openWebSocket: ")
        return MyWebSocket(handshake)
    }

    inner class MyWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            Log.d(TAG, "onOpen: ")
            // Connection opened
            sockets.add(this)
            try {
                send("WebSocket connection established")
                val message = """
            {
                "command": "LOAD_MEDIA",
                "url": "ss"",
                "mimeType": "mimeType"
            }
        """.trimIndent()
                send("Server received: $message")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, remote: Boolean) {
            // Connection closed
            Log.d(TAG, "onClose: $reason")
            sockets.remove(this)
        }

        override fun onMessage(frame: WebSocketFrame) {
            val message = frame.textPayload
            Log.d(TAG, "onMessage: ${frame.textPayload}")
            try {
                send("Server received: $message")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onPong(frame: WebSocketFrame) {
            // Handle pong frames (keep-alive)
            Log.d(TAG, "onPong: ")
        }

        override fun onException(exception: IOException?) {
            Log.e(TAG, "onException:  socket start ${exception?.message}")
            exception?.printStackTrace()
        }
    }

    fun notifyVideoAction(cmd: CommandCastType) {
        Log.d(TAG, "notifyNewVideo: ")
//        val availablePort = MainActivity.findAvailablePort()
//        val videoServer = VideoServer(context, host, availablePort, uri, "video/*")
//        videoServer.start()
//
//        sockets.forEach { item ->
//            item.close(WebSocketFrame.CloseCode.NormalClosure, "Cast New", true)
//        }
//        if (videoServer.isAlive) {
//            val message = """
//            {
//                "command": "LOAD_MEDIA",
//                "url": "${videoServer.videoURL}",
//                "mimeType": "$mimeType"
//            }
//        """.trimIndent()
//
//            // Broadcast the message to all connected clients
//
//            scope.launch {
//                sockets.forEach { socket ->
//                    try {
//                        socket.send(message)
//                    } catch (e: Exception) {
//                        Log.w(TAG, "notifyNewVideo: ${e.message}")
//                        e.printStackTrace()
//                    }
//                }
//            }
//        } else {
//            Log.e(TAG, "notifyNewVideo: error")
//        }
    }

    fun notifyNewVideo(uri: Uri, mimeType: String) {
        Log.d(TAG, "notifyNewVideo: ")

        val availablePort = MainActivity.findAvailablePort()
        val videoServer = VideoServer(context, host, availablePort, uri, "video/*")
        videoServer.start()

        if (videoServer.isAlive) {
            val message = """
            {
                "command": "LOAD_MEDIA",
                "url": "${videoServer.videoURL}",
                "mimeType": "$mimeType"
            }
        """.trimIndent()

            // Broadcast the message to all connected clients

            scope.launch {
                sockets.forEach { socket ->
                    try {
                        socket.send(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "notifyNewVideo: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } else {
            Log.e(TAG, "notifyNewVideo: error")
        }
    }

    fun notifyNewImage(uri: Uri, mimeType: String) {
        Log.d(TAG, "notifyNewImage: ")
        val availablePort = MainActivity.findAvailablePort()
        val imageServer = ImageServer(context, host, availablePort, uri, mimeType)
        imageServer.start()

        if (imageServer.isAlive) {
            val message = """
            {
                "command": "LOAD_MEDIA",
                "url": "${imageServer.videoURL}",
                "mimeType": "$mimeType"
            }
        """.trimIndent()

            // Broadcast the message to all connected clients

            scope.launch {
                sockets.forEach { socket ->
                    try {
                        socket.send(message)
                    } catch (e: Exception) {
                        Log.w(TAG, "notifyNewVideo: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } else {
            Log.e(TAG, "notifyNewVideo: error")
        }
    }
}