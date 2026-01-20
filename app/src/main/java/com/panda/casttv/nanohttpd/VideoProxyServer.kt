package com.panda.casttv.nanohttpd

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL

private val TAG = "YtbActivity"


class VideoProxyServer(
    private val realYoutubeUrl: String,
    private val host: String,
    private val port: Int,
) : NanoHTTPD(null, port) {

    val url: String
        get() = "http://$host:$port"

    override fun serve(session: IHTTPSession): Response {
        val rangeHeader = session.headers["range"] // e.g., "bytes=0-"
        Log.d(TAG, "Request: $rangeHeader from ${session.remoteIpAddress}")

        return try {
            val url = URL(realYoutubeUrl)
            val connection = url.openConnection() as HttpURLConnection

            // 1. FORWARD THE RANGE HEADER
            // If Safari asks for bytes=5000-10000, we must ask YouTube for the same.
            if (rangeHeader != null) {
                connection.setRequestProperty("Range", rangeHeader)
            }

            // Spoof User Agent to ensure we get a friendly stream
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (iPad; CPU OS 12_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)"
            )

            connection.connect()

            val responseCode = connection.responseCode
            val inputStream = connection.inputStream

            // 2. GET CONTENT LENGTH
            // NanoHTTPD needs to know how many bytes we are sending in this chunk
            val contentLength = connection.contentLengthLong
            val contentType = connection.contentType ?: "video/mp4"

            // 3. DETERMINE STATUS (200 vs 206)
            // If we sent a Range header, YouTube usually returns 206.
            val status =
                if (responseCode == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK

            // 4. CREATE RESPONSE
            // We use FixedLength because Safari hates Chunked encoding for MP4
            val response = newFixedLengthResponse(status, contentType, inputStream, contentLength)

            // 5. FORWARD CRITICAL HEADERS
            // Safari needs "Content-Range" and "Accept-Ranges" to know it can seek.
            val contentRange = connection.getHeaderField("Content-Range")
            val acceptRanges = connection.getHeaderField("Accept-Ranges")

            if (contentRange != null) response.addHeader("Content-Range", contentRange)
            if (acceptRanges != null) response.addHeader("Accept-Ranges", acceptRanges)

            // Always say we accept bytes (even if YouTube didn't explicitly say it, we want to try)
            response.addHeader("Accept-Ranges", "bytes")

            response
        } catch (e: Exception) {
            Log.e(TAG, "Proxy Error: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                NanoHTTPD.MIME_PLAINTEXT,
                "Error: " + e.message
            )
        }
    }
}