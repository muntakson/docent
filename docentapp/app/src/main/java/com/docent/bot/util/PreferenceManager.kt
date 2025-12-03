package com.docent.bot.util

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "docent_prefs"

        private const val KEY_AMR_IP = "amr_ip"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_PROJECTOR_HOST = "projector_host"
        private const val KEY_PROJECTOR_PORT = "projector_port"
        private const val KEY_PROJECTOR_TYPE = "projector_type"
        private const val KEY_PROJECTOR_NAME = "projector_name"
        private const val KEY_VIDEO_URI = "video_uri"
        private const val KEY_VIDEO_NAME = "video_name"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_RATE = "tts_rate"

        const val DEFAULT_AMR_IP = "192.168.219.42"
        const val DEFAULT_BACKEND_URL = "https://docent.rongrong.org"
        const val DEFAULT_TTS_VOICE = "ko-KR-Wavenet-A"
        const val DEFAULT_TTS_RATE = 1.0f
    }

    var amrIp: String
        get() = prefs.getString(KEY_AMR_IP, DEFAULT_AMR_IP) ?: DEFAULT_AMR_IP
        set(value) = prefs.edit().putString(KEY_AMR_IP, value).apply()

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value).apply()

    var projectorHost: String?
        get() = prefs.getString(KEY_PROJECTOR_HOST, null)
        set(value) = prefs.edit().putString(KEY_PROJECTOR_HOST, value).apply()

    var projectorPort: Int
        get() = prefs.getInt(KEY_PROJECTOR_PORT, 7000)
        set(value) = prefs.edit().putInt(KEY_PROJECTOR_PORT, value).apply()

    var projectorType: String?
        get() = prefs.getString(KEY_PROJECTOR_TYPE, null)
        set(value) = prefs.edit().putString(KEY_PROJECTOR_TYPE, value).apply()

    var projectorName: String?
        get() = prefs.getString(KEY_PROJECTOR_NAME, null)
        set(value) = prefs.edit().putString(KEY_PROJECTOR_NAME, value).apply()

    var videoUri: String?
        get() = prefs.getString(KEY_VIDEO_URI, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_URI, value).apply()

    var videoName: String?
        get() = prefs.getString(KEY_VIDEO_NAME, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_NAME, value).apply()

    var ttsVoice: String
        get() = prefs.getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE) ?: DEFAULT_TTS_VOICE
        set(value) = prefs.edit().putString(KEY_TTS_VOICE, value).apply()

    var ttsRate: Float
        get() = prefs.getFloat(KEY_TTS_RATE, DEFAULT_TTS_RATE)
        set(value) = prefs.edit().putFloat(KEY_TTS_RATE, value).apply()

    fun saveProjector(host: String, port: Int, type: String, name: String) {
        prefs.edit().apply {
            putString(KEY_PROJECTOR_HOST, host)
            putInt(KEY_PROJECTOR_PORT, port)
            putString(KEY_PROJECTOR_TYPE, type)
            putString(KEY_PROJECTOR_NAME, name)
            apply()
        }
    }

    fun clearProjector() {
        prefs.edit().apply {
            remove(KEY_PROJECTOR_HOST)
            remove(KEY_PROJECTOR_PORT)
            remove(KEY_PROJECTOR_TYPE)
            remove(KEY_PROJECTOR_NAME)
            apply()
        }
    }

    fun hasProjector(): Boolean = projectorHost != null
}
