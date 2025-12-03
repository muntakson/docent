package com.docent.bot.model

data class ProjectorDevice(
    val name: String,
    val host: String,
    val port: Int,
    val type: String,  // "EShare", "AirPlay", "DLNA", "Chromecast"
    var isSelected: Boolean = false
) {
    val id: String get() = "$host:$port"

    val displayName: String get() {
        val cleanName = name.substringBefore("@").trim()
        return if (cleanName.isNotEmpty()) cleanName else "$type ($host)"
    }
}

data class VideoFile(
    val uri: android.net.Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val duration: Long = 0
) {
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getFormattedDuration(): String {
        if (duration <= 0) return ""
        val seconds = duration / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
}

sealed class StreamingState {
    object Idle : StreamingState()
    object Preparing : StreamingState()
    data class Streaming(val videoName: String, val deviceName: String) : StreamingState()
    data class Paused(val videoName: String, val deviceName: String) : StreamingState()
    object Stopped : StreamingState()
    data class Error(val message: String) : StreamingState()
}
