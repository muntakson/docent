package com.docent.bot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.docent.bot.R
import com.docent.bot.model.ProjectorDevice
import com.docent.bot.model.StreamingState
import com.docent.bot.model.VideoFile
import com.docent.bot.util.NetworkUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MediaServerService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var httpServer: VideoServer? = null
    private var serverPort = 8080

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private var currentVideoFile: VideoFile? = null
    private var currentDevice: ProjectorDevice? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    inner class LocalBinder : Binder() {
        fun getService(): MediaServerService = this@MediaServerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "미디어 서버",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "동영상 스트리밍 서비스"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("도슨트봇")
            .setContentText("미디어 서버 실행 중")
            .setSmallIcon(R.drawable.ic_video)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startServer() {
        try {
            httpServer = VideoServer(serverPort, contentResolver).apply {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }
        } catch (e: Exception) {
            // Try alternate port
            serverPort = 8081
            try {
                httpServer = VideoServer(serverPort, contentResolver).apply {
                    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun stopServer() {
        httpServer?.stop()
        httpServer = null
    }

    fun setVideoFile(videoFile: VideoFile) {
        currentVideoFile = videoFile
        httpServer?.setVideoUri(videoFile.uri, videoFile.mimeType, videoFile.size)
    }

    fun getVideoUrl(): String? {
        val localIp = NetworkUtils.getLocalIpAddress(this) ?: return null
        return "http://$localIp:$serverPort/video"
    }

    fun startStreaming(device: ProjectorDevice) {
        val videoFile = currentVideoFile ?: return
        val videoUrl = getVideoUrl() ?: return

        currentDevice = device
        _streamingState.value = StreamingState.Preparing

        serviceScope.launch {
            val success = when (device.type) {
                "EShare" -> tryEShareStreaming(device, videoUrl, videoFile.name)
                "AirPlay" -> tryAirPlayStreaming(device, videoUrl, videoFile.name)
                "Chromecast" -> tryChromecastStreaming(device, videoUrl)
                else -> tryGenericStreaming(device, videoUrl)
            }

            if (success) {
                _streamingState.value = StreamingState.Streaming(videoFile.name, device.displayName)
            } else {
                _streamingState.value = StreamingState.Error("스트리밍 시작 실패")
            }
        }
    }

    fun stopStreaming() {
        val device = currentDevice ?: return

        serviceScope.launch {
            try {
                // Send stop command based on device type
                when (device.type) {
                    "AirPlay", "EShare" -> {
                        sendStopCommand(device.host, device.port)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _streamingState.value = StreamingState.Stopped
            currentDevice = null
        }
    }

    private suspend fun tryEShareStreaming(device: ProjectorDevice, videoUrl: String, title: String): Boolean {
        // Try AirPlay-compatible endpoint first
        val portsToTry = listOf(device.port, 7000, 7100)

        for (port in portsToTry) {
            // Try binary plist format
            if (tryAirPlayWithContentType(device.host, port, videoUrl, "application/x-apple-binary-plist", title)) {
                return true
            }

            // Try text/parameters format
            if (tryAirPlayWithContentType(device.host, port, videoUrl, "text/parameters", title)) {
                return true
            }

            // Try JSON stream endpoint
            if (tryJsonStreamEndpoint(device.host, port, videoUrl, title)) {
                return true
            }
        }

        return false
    }

    private suspend fun tryAirPlayStreaming(device: ProjectorDevice, videoUrl: String, title: String): Boolean {
        val portsToTry = listOf(device.port, 7000, 7100)

        for (port in portsToTry) {
            if (tryAirPlayWithContentType(device.host, port, videoUrl, "text/parameters", title)) {
                return true
            }
            if (tryAirPlayWithContentType(device.host, port, videoUrl, "application/x-apple-binary-plist", title)) {
                return true
            }
        }

        return false
    }

    private suspend fun tryAirPlayWithContentType(
        host: String,
        port: Int,
        videoUrl: String,
        contentType: String,
        title: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val body = "Content-Location: $videoUrl\nStart-Position: 0.0\n"

                val request = Request.Builder()
                    .url("http://$host:$port/play")
                    .post(body.toRequestBody(contentType.toMediaType()))
                    .header("User-Agent", "iTunes/12.2 (Macintosh; OS X 10.10.5)")
                    .header("X-Apple-Session-ID", java.util.UUID.randomUUID().toString())
                    .header("X-Apple-Device-ID", "0x0000000000000001")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 101
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun tryJsonStreamEndpoint(host: String, port: Int, videoUrl: String, title: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = """{"url":"$videoUrl","title":"$title"}"""

                val request = Request.Builder()
                    .url("http://$host:$port/stream")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun tryChromecastStreaming(device: ProjectorDevice, videoUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = """{"v":"$videoUrl"}"""

                val request = Request.Builder()
                    .url("http://${device.host}:${device.port}/apps/YouTube")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun tryGenericStreaming(device: ProjectorDevice, videoUrl: String): Boolean {
        val endpoints = listOf("/play", "/video", "/stream", "/media", "/cast")

        return withContext(Dispatchers.IO) {
            for (endpoint in endpoints) {
                try {
                    val body = "Content-Location: $videoUrl\n"

                    val request = Request.Builder()
                        .url("http://${device.host}:${device.port}$endpoint")
                        .post(body.toRequestBody("text/parameters".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) return@withContext true
                    }
                } catch (e: Exception) {
                    // Try next endpoint
                }
            }
            false
        }
    }

    private suspend fun sendStopCommand(host: String, port: Int) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$host:$port/stop")
                    .post("".toRequestBody("text/parameters".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    // HTTP Server for serving video files
    private inner class VideoServer(
        port: Int,
        private val contentResolver: ContentResolver
    ) : NanoHTTPD(port) {

        private var videoUri: Uri? = null
        private var videoMimeType: String = "video/mp4"
        private var videoSize: Long = 0

        fun setVideoUri(uri: Uri, mimeType: String, size: Long) {
            videoUri = uri
            videoMimeType = mimeType
            videoSize = size
        }

        override fun serve(session: IHTTPSession): Response {
            if (session.uri != "/video") {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }

            val uri = videoUri ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "No video selected"
            )

            val rangeHeader = session.headers["range"]

            return if (rangeHeader != null) {
                servePartialContent(uri, rangeHeader)
            } else {
                serveFullContent(uri)
            }
        }

        private fun serveFullContent(uri: Uri): Response {
            return try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Cannot open file")

                newFixedLengthResponse(Response.Status.OK, videoMimeType, inputStream, videoSize)
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
            }
        }

        private fun servePartialContent(uri: Uri, rangeHeader: String): Response {
            return try {
                // Parse range header: bytes=0-1023
                val range = rangeHeader.replace("bytes=", "")
                val rangeParts = range.split("-")
                val start = rangeParts[0].toLongOrNull() ?: 0
                val end = if (rangeParts.size > 1 && rangeParts[1].isNotEmpty()) {
                    rangeParts[1].toLong()
                } else {
                    videoSize - 1
                }

                val contentLength = end - start + 1

                val inputStream = contentResolver.openInputStream(uri)
                    ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Cannot open file")

                inputStream.skip(start)

                val response = newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT,
                    videoMimeType,
                    LimitedInputStream(inputStream, contentLength),
                    contentLength
                )

                response.addHeader("Content-Range", "bytes $start-$end/$videoSize")
                response.addHeader("Accept-Ranges", "bytes")

                response
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
            }
        }
    }

    // Limited input stream for partial content
    private class LimitedInputStream(
        private val source: InputStream,
        private var remaining: Long
    ) : InputStream() {

        override fun read(): Int {
            if (remaining <= 0) return -1
            val result = source.read()
            if (result != -1) remaining--
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val result = source.read(b, off, toRead)
            if (result != -1) remaining -= result
            return result
        }

        override fun close() {
            source.close()
        }
    }

    companion object {
        private const val CHANNEL_ID = "media_server_channel"
        private const val NOTIFICATION_ID = 1002
    }
}
