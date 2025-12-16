package com.panda.casttv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastButtonFactory
import com.panda.casttv.chrome_cast.state.ChromeCastState
import com.panda.casttv.nanohttpd.VideoServer
import com.panda.casttv.nanohttpd.VideoServiceInit
import com.panda.casttv.nanohttpd.VideoWebSocket
import com.panda.casttv.ui.theme.CastTVResearchTheme
import fi.iki.elonen.NanoHTTPD

class MainActivity : AppCompatActivity() {

    private val TAGS = "nanohttpd"

    private var serverInit: VideoServiceInit? = null

    private lateinit var chromeCastState: ChromeCastState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chromeCastState = object : ChromeCastState(this) {

        }

        enableEdgeToEdge()
        setContent {
            CastTVResearchTheme {

                val pickVideo = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        serverInit?.server?.notifyNewVideo(uri, "video/*")
//                        }
                    }
                }

                val pickImage = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        val host = wifiIPAddress
                        if (!host.isNullOrEmpty()) {
                            serverInit?.server?.notifyNewImage(uri, "image/*")
                        }
                    }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding),
                        chromeCastState = chromeCastState,
                        onCastVideo = {
                            pickVideo.launch("video/*")
                        },
                        onCastImage = {
                            pickImage.launch("image/*")
                        }
                    )
                }
            }

        }
        startServerInit()
    }

    fun startServerInit() {
        val host = wifiIPAddress
        if (!host.isNullOrEmpty()) {
            val port = findAvailablePort()
            serverInit = VideoServiceInit(this, host, port)
            try {
                serverInit?.start()
                Log.d(TAGS, "startServerSocket: $port")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        chromeCastState.run {
            mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            castSessionManager.addSessionManagerListener(sessionManagerListener)
            castReceiverContext?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        chromeCastState.run {
            mediaRouter.removeCallback(mediaRouterCallback)
            castSessionManager.removeSessionManagerListener(sessionManagerListener)
            castReceiverContext?.stop()
        }
    }

    val wifiIPAddress: String?
        get() {
            val connectivityManager =
                this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

            // Check if connected via Wi-Fi
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager =
                    this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                // Deprecated method is used as it's simple for this use case.
                // For modern approach use NsdManager or WifiP2pManager, but this works for basic IP retrieval
                @Suppress("DEPRECATION")
                val ipAddress = Formatter.formatIpAddress(wifiInfo.ipAddress)
                return ipAddress
            }
            return null
        }

    companion object {
        fun findAvailablePort(): Int {
            return try {
                // Bind to port 0 tells the OS to pick the first available port
                val socket = java.net.ServerSocket(0)
                val port = socket.localPort
                socket.close()
                port
            } catch (e: Exception) {
                // Fallback or error handling
                8080
            }
        }
    }
}

@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    chromeCastState: ChromeCastState,
    onCastVideo: () -> Unit,
    onCastImage: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.Blue)
                .padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            AndroidView(
                factory = { _ ->
                    MediaRouteButton(context).also { button ->
                        CastButtonFactory.setUpMediaRouteButton(
                            context,
                            button
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(25.dp)
            )
        }
        chromeCastState.items.forEach { device ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    device.name,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text(
                "Cast Image",
                color = Color.White,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
                    .background(Color.Blue)
                    .clickable {
                        onCastImage()
                    }
                    .padding(vertical = 12.dp)
            )

            Text(
                "Cast Video",
                color = Color.White,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
                    .background(Color.Blue)
                    .clickable {
                        onCastVideo()
                    }
                    .padding(vertical = 12.dp)
            )
        }
    }
}