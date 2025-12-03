package com.docent.bot.service

import com.docent.bot.model.Route
import com.docent.bot.model.RoutePoint
import com.docent.bot.model.Zone
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class BackendApiService(private val backendUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Default zones with Korean speech text
    private val defaultZones = listOf(
        Zone(
            id = "zone1",
            name = "웰컴 존",
            pointName = "",
            speech = "안녕! 나는 서희봇이야! 만나서 반가워! 오늘 너희들과 함께 우리 도서관을 탐험할 거야! 준비됐니? 그럼 출발!"
        ),
        Zone(
            id = "zone2",
            name = "미디어 존",
            pointName = "",
            speech = "여기는 미디어 존이야! AI와 반도체에 대해 재미있는 영상을 볼 수 있어. 화면을 잘 봐봐!"
        ),
        Zone(
            id = "zone3",
            name = "인터렉티브 존",
            pointName = "",
            speech = "와! 여기가 인터렉티브 존이야! 직접 만지고 체험할 수 있는 공간이야. 재미있게 놀아봐!"
        ),
        Zone(
            id = "zone4",
            name = "홀로그램 존",
            pointName = "",
            speech = "우와! 홀로그램 존에 도착했어! 물고기 로봇과 반도체에 대해 알아볼 수 있어. 신기하지?"
        ),
        Zone(
            id = "zone5",
            name = "미래역량체험 존",
            pointName = "",
            speech = "여기는 미래역량체험 존이야! 미래에 필요한 능력들을 재미있게 배울 수 있어!"
        ),
        Zone(
            id = "zone6",
            name = "메모리 존",
            pointName = "",
            speech = "마지막으로 메모리 존이야! 오늘 우리가 함께한 시간, 잊지 말고 기억해줘! 다음에 또 만나!"
        )
    )

    // Get zones from backend or return defaults
    suspend fun getZones(): Result<List<Zone>> = withContext(Dispatchers.IO) {
        try {
            // Try to fetch from backend API
            val response = get("/api/zones")
            val json = JsonParser.parseString(response).asJsonArray

            val zones = json.map { element ->
                val obj = element.asJsonObject
                Zone(
                    id = obj.get("id")?.asString ?: "",
                    name = obj.get("name")?.asString ?: "",
                    pointName = obj.get("pointName")?.asString ?: "",
                    speech = obj.get("speech")?.asString ?: ""
                )
            }

            Result.success(zones.ifEmpty { defaultZones })
        } catch (e: Exception) {
            // Return default zones if backend is not available
            Result.success(defaultZones)
        }
    }

    // Get routes from backend
    suspend fun getRoutes(): Result<Map<String, Route>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/routes")
            val json = JsonParser.parseString(response).asJsonObject

            val routes = mutableMapOf<String, Route>()
            json.keySet().forEach { key ->
                val routeObj = json.getAsJsonObject(key)
                val pointsArray = routeObj.getAsJsonArray("points")

                val points = pointsArray.map { pointElement ->
                    val pointObj = pointElement.asJsonObject
                    RoutePoint(
                        name = pointObj.get("name")?.asString ?: "",
                        stayTime = pointObj.get("stayTime")?.asInt ?: 10
                    )
                }

                routes[key] = Route(
                    name = routeObj.get("name")?.asString ?: key,
                    points = points,
                    createdAt = routeObj.get("createdAt")?.asString ?: ""
                )
            }

            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Text-to-Speech
    suspend fun synthesizeSpeech(
        text: String,
        voice: String = "ko-KR-Wavenet-A",
        rate: Float = 1.0f,
        pitch: Float = 0.0f
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "text" to text,
                "voice" to voice,
                "rate" to rate,
                "pitch" to pitch
            )

            val request = Request.Builder()
                .url("$backendUrl/api/tts")
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("TTS failed: ${response.code}")
                }
                val audioData = response.body?.bytes() ?: throw Exception("Empty TTS response")
                Result.success(audioData)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get available TTS voices
    suspend fun getTTSVoices(): Result<List<TTSVoice>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/api/tts/voices")
            val json = JsonParser.parseString(response).asJsonObject
            val voicesArray = json.getAsJsonArray("voices")

            val voices = voicesArray.map { element ->
                val obj = element.asJsonObject
                TTSVoice(
                    name = obj.get("name")?.asString ?: "",
                    gender = obj.get("gender")?.asString ?: "",
                    description = obj.get("description")?.asString ?: ""
                )
            }

            Result.success(voices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Health check
    suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$backendUrl/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun get(endpoint: String): String {
        val request = Request.Builder()
            .url("$backendUrl$endpoint")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }
}

data class TTSVoice(
    val name: String,
    val gender: String,
    val description: String
)
