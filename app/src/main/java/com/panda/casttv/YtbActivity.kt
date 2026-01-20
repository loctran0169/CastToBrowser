package com.panda.casttv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.panda.casttv.nanohttpd.VideoProxyServer
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

private val TAG = "YtbActivity"

class YtbActivity : ComponentActivity() {
    var scanFail = false
    var port = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Greeting(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    @Composable
    fun Greeting(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        Box(modifier.fillMaxSize()) {
            val webView = remember {
                WebView(context)
            }

            fun extractVideoUrl() {
                // This JS looks for the <video> tag and sends the 'src' back to Android
                val js = """
            (function() {
                var video = document.querySelector('video');
                if (video) {
                    // Send video URL to Android
                    return video.src; 
                }
                return null;
            })();
        """

                webView.evaluateJavascript(js) { value ->
                    // value will be the URL (e.g., "https://googlevideo.com/...")
                    if (value.contains("googlevideo.com")) {
                        val rawUrl = value.replace("\"", "")
                        Log.d(TAG, "Found Video: $rawUrl")
                        // TRIGGER STEP 3 HERE: Start the proxy and cast
                    }
                }
            }

            AndroidView(factory = { context ->
                webView.apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val requestUrl = request?.url.toString()
//                        Log.d(TAG, "shouldInterceptRequest: $requestUrl")
                            // Look for .m3u8 in the URL
                            if (requestUrl.contains("m3u8")) {
                                Log.d(TAG, requestUrl)
                            }

                            if (requestUrl.contains("googlevideo.com")) {
                                lifecycleScope.launch {
                                    if (scanFail) return@launch
                                    Log.d(TAG, requestUrl)
                                    val tvHost = "192.168.0.164"
                                    val phoneHost = wifiIPAddress ?: ""
                                    val port = if (port == 0) findTvPort(tvHost) else port
                                    if (port == -1) {
                                        scanFail = true
                                        return@launch
                                    }
                                    startCastProcess(tvHost, phoneHost, "https://m.youtube.com/watch?v=tAaUbRt6jqM")
//                                    startCastProcess(
//                                        tvIp = tvHost,
//                                        phoneIp = phoneHost,
//                                        rawYoutubeUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
//                                    )
//                                    startCastProcess(tvHost, phoneHost, requestUrl)

//                                    val testVideo =
//                                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
//                                    castVideoToTv(
//                                        tvIp = "$tvHost:$port",
//                                        tvControlPath = "upnp/control/AVTransport1",
//                                        videoUrl = testVideo.ifEmpty { requestUrl }
//                                    )
                                    scanFail = true
                                }
                                return null
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            extractVideoUrl()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {

                    }
                    settings.javaScriptEnabled = true // Required for YouTube
                    settings.domStorageEnabled = true // Required for YouTube
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false

                        userAgentString =
                            "Mozilla/5.0 (iPad; CPU OS 12_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1 Mobile/15E148 Safari/604.1"
                    }
                    loadUrl("https://m.youtube.com/watch?v=tAaUbRt6jqM")
                }
            })
        }
    }

    suspend fun playCommand(controlUrl: String) {
        val playXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
            <s:Body>
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                    <InstanceID>0</InstanceID>
                    <Speed>1</Speed>
                </u:Play>
            </s:Body>
        </s:Envelope>
    """.trimIndent()

        try {
            val url = URL(controlUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                setRequestProperty(
                    "SOAPAction",
                    "\"urn:schemas-upnp-org:service:AVTransport:1#Play\""
                )
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(playXml)
            writer.flush()
            writer.close()

            Log.d(TAG, "Play Command Sent: ${conn.responseCode}")
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Define this at the class level so it doesn't get garbage collected
    var proxyServer: VideoProxyServer? = null

    suspend fun startCastProcess(tvIp: String, phoneIp: String, rawYoutubeUrl: String) {
        Log.d(TAG, "startCastProcess: ")
        suspend fun castToTvWithMetadata(controlUrl: String, videoUrl: String) {
            Log.d(TAG, "castToTvWithMetadata: controlUrl $controlUrl")
            Log.d(TAG, "castToTvWithMetadata: videoUrl $videoUrl")
            // This Metadata tells the TV: "This is a video, not music or an image."
            // We must Escape HTML characters (< becomes &lt;) because it goes inside another XML.
            val metadata = """
                &lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dc="http://purl.org/dc/elements/1.1/"&gt;
                   &lt;item id="1" parentID="0" restricted="1"&gt;
                      &lt;dc:title&gt;YouTube Stream&lt;/dc:title&gt;
                      &lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;
                      &lt;res protocolInfo="http-get:*:video/mp4:*"&gt;$videoUrl&lt;/res&gt;
                   &lt;/item&gt;
                &lt;/DIDL-Lite&gt;
            """.trimIndent()

            val soapSetUri = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                    <s:Body>
                        <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                            <CurrentURI>$videoUrl</CurrentURI>
                            <CurrentURIMetaData>$metadata</CurrentURIMetaData>
                        </u:SetAVTransportURI>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()

            // Helper function to send the XML
            fun sendSoapRequest(url: String, xml: String, action: String): Int {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                connection.setRequestProperty(
                    "SOAPAction",
                    "\"urn:schemas-upnp-org:service:AVTransport:1#$action\""
                )
                connection.setRequestProperty("Connection", "Close")

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(xml)
                writer.flush()
                writer.close()

                return connection.responseCode
            }

            val responseCode = sendSoapRequest(controlUrl, soapSetUri, "SetAVTransportURI")

            Log.d(TAG, "Set URI Response: $responseCode - ")

            if (responseCode == 200) {
                playCommand(controlUrl)
            }
        }

        withContext(Dispatchers.IO) {
            try {
                // 2. START the Proxy Server
                Log.d(TAG, "Starting Proxy for: $rawYoutubeUrl")
                proxyServer = VideoProxyServer(
                    rawYoutubeUrl,
                    phoneIp,
                    MainActivity.findAvailablePort(),
                )
                proxyServer?.start()

                if (proxyServer?.isAlive != true) {
                    Log.e(TAG, "startCastProcess: service not started", )
                    return@withContext
                }

                Log.d(TAG, "TV will play: ${proxyServer?.url}")

                val tvControlUrl = "http://$tvIp:9197/upnp/control/AVTransport1"

                castToTvWithMetadata(tvControlUrl, proxyServer?.url ?: "")

            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        }
    }

    suspend fun castVideoToTv(tvIp: String, tvControlPath: String, videoUrl: String) {
        Log.d(TAG, "castVideoToTv: $tvIp/$tvControlPath - $videoUrl")
        withContext(Dispatchers.IO) {
            try {

                // 2. Build URL with the detected port
                // Note: tvControlPath usually starts with "/", e.g., "/upnp/control/AVTransport1"
                val controlUrl = "http://$tvIp/$tvControlPath"
                Log.d(TAG, "Sending to: $controlUrl")

                // 3. Create SOAP Payload (Same as before)
                val soapXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                    <s:Body>
                        <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                            <CurrentURI>$videoUrl</CurrentURI>
                            <CurrentURIMetaData></CurrentURIMetaData>
                        </u:SetAVTransportURI>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()

                // 4. Send Request
                val url = URL(controlUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    doOutput = true
                    setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                    setRequestProperty(
                        "SOAPAction",
                        "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\""
                    )
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(soapXml)
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                Log.d(
                    TAG,
                    "Set URI Response: $responseCode - ${connection.responseMessage} - ${connection.url}"
                )

                // 5. CRITICAL: You must send "Play" command after setting the URI
                if (responseCode == 200) {
                    playCommand(controlUrl)
                }

                connection.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun findTvPort(tvIp: String): Int {
        val commonPorts = listOf(
            52235, // Samsung, Sony, Generic DLNA
            9197,  // LG, Some Samsung
            8060,  // Roku
            8008,  // Chromecast / Google TV
            8009,  // Chromecast
            1900,  // Chromecast
            7676,  // Older Samsung
            8000,  // Generic AV
            55000, 55001, // Generic Linux/UPnP,
            80, 443, 1900, 50000 - 50010, 8080, 8096, 2869
        )

        Log.d(TAG, "Scanning ports on $tvIp...")

        return withContext(Dispatchers.IO) {
            for (port in commonPorts) {
                try {
                    val socket = Socket()
                    // Try to connect with a short timeout (200ms)
                    socket.connect(InetSocketAddress(tvIp, port), 200)
                    socket.close()

                    Log.d(TAG, "SUCCESS! Found open port: $port")
                    return@withContext port // Return the first open port found
                } catch (e: Exception) {
                    // Port is closed, continue to next
                }
            }
            Log.e(TAG, "No common DLNA ports found.")
            return@withContext -1
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
}