package com.panda.casttv.nanohttpd

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.FileNotFoundException
import java.io.IOException


class VideoServer(
    val context: Context,
    val host: String,
    val port: Int,
    val uri: Uri,
    val mimeType: String,
) : NanoHTTPD(host, port) {

    val videoURL: String
        get() = "http://$host:$port/"

    private val TAG: String
        get() = this::class.java.simpleName

    override fun serve(session: IHTTPSession?): Response {
        var range: String? = null
        val headers = session?.headers
        if (headers != null) {
            for (key in headers.keys) {
                if ("range" == key) {
                    range = headers[key]
                    break
                }
            }
        }
        return if (range != null) {
            try {
                getPartialResponse(mimeType, range)
            } catch (e: IOException) {
                throw java.lang.RuntimeException(e)
            }
        } else {
            responseVideoStream()
        }
    }

    fun responseVideoStream(): Response {
        try {
            val fis = context.contentResolver.openInputStream(uri)
            return newChunkedResponse(Response.Status.OK, mimeType, fis)
        } catch (e: FileNotFoundException) {
            throw RuntimeException(e)
        }
    }

    fun Context.getFileLengthFromUri(uri: Uri): Long {
        var size: Long = 0
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = it.getLong(sizeIndex)
                }
            }
        }
        return size
    }

    @Throws(IOException::class)
    private fun getPartialResponse(mimeType: String?, rangeHeader: String): Response {
        Log.d(TAG, "getPartialResponse: ")
        val fileLength = context.getFileLengthFromUri(uri)
        val rangeValue = rangeHeader.trim { it <= ' ' }.substring("bytes=".length)
        val start: Long
        var end: Long
        if (rangeValue.startsWith("-")) {
            end = fileLength - 1
            start = (fileLength - 1 - rangeValue.substring("-".length).toLong())
        } else {
            val range: Array<String?> =
                rangeValue.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            start = range[0]!!.toLong()
            end = if (range.size > 1) range[1]!!.toLong() else
                fileLength - 1
        }
        if (end > fileLength - 1) {
            end = fileLength - 1
        }
        if (start <= end) {
            val contentLength = end - start + 1
            val fileInputStream =
                context.contentResolver.openInputStream(uri)
                    ?: return newChunkedResponse(Response.Status.INTERNAL_ERROR, "text/html", null)
            fileInputStream.skip(start)
            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                mimeType,
                fileInputStream,
                contentLength
            )
            response.addHeader("Content-Length", contentLength.toString() + "")
            response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            response.addHeader("Content-Type", mimeType)
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            response.addHeader("Pragma", "no-cache")
            response.addHeader("Expires", "0")
            return response
        } else {
            return newChunkedResponse(Response.Status.INTERNAL_ERROR, "text/html", null)
        }
    }
}