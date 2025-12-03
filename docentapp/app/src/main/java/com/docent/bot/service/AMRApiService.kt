package com.docent.bot.service

import com.docent.bot.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AMRApiService(private val amrIp: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl: String
        get() = "http://$amrIp"

    // Status endpoints
    suspend fun getPose(): Result<RobotPose> = withContext(Dispatchers.IO) {
        try {
            val response = get("/reeman/pose")
            val json = JsonParser.parseString(response).asJsonObject
            Result.success(RobotPose(
                x = json.get("x")?.asDouble ?: 0.0,
                y = json.get("y")?.asDouble ?: 0.0,
                theta = json.get("theta")?.asDouble ?: 0.0
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNavStatus(): Result<NavStatus> = withContext(Dispatchers.IO) {
        try {
            val response = get("/reeman/nav_status")
            val json = JsonParser.parseString(response).asJsonObject
            Result.success(NavStatus(
                res = json.get("res")?.asInt ?: 6,
                reason = json.get("reason")?.asInt ?: 0,
                goal = json.get("goal")?.asString ?: "",
                dist = json.get("dist")?.asDouble ?: 0.0,
                mileage = json.get("mileage")?.asDouble ?: 0.0
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBaseEncode(): Result<BaseEncode> = withContext(Dispatchers.IO) {
        try {
            val response = get("/reeman/base_encode")
            val json = JsonParser.parseString(response).asJsonObject
            Result.success(BaseEncode(
                battery = json.get("battery")?.asInt ?: 0,
                chargeFlag = json.get("chargeFlag")?.asInt ?: 1,
                emergencyButton = json.get("emergencyButton")?.asInt ?: 1
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMode(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = get("/reeman/get_mode")
            val json = JsonParser.parseString(response).asJsonObject
            Result.success(json.get("mode")?.asInt ?: 2)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Waypoint endpoints
    suspend fun getWaypoints(): Result<List<Waypoint>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/reeman/position")
            val json = JsonParser.parseString(response).asJsonObject
            val waypointsArray = json.getAsJsonArray("waypoints") ?: return@withContext Result.success(emptyList())

            val waypoints = waypointsArray.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    val poseObj = obj.getAsJsonObject("pose")
                    Waypoint(
                        name = obj.get("name")?.asString ?: return@mapNotNull null,
                        type = obj.get("type")?.asString ?: "normal",
                        pose = RobotPose(
                            x = poseObj.get("x")?.asDouble ?: 0.0,
                            y = poseObj.get("y")?.asDouble ?: 0.0,
                            theta = poseObj.get("theta")?.asDouble ?: 0.0
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(waypoints)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Navigation commands
    suspend fun navigateTo(pointName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf("point" to pointName)
            post("/cmd/nav_name", gson.toJson(body))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelNavigation(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            post("/cmd/cancel_goal", "{}")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun navigateToCharge(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            post("/cmd/charge", "{}")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Movement commands
    suspend fun move(distance: Int, forward: Boolean = true, speed: Double = 0.3): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "distance" to distance,
                "direction" to if (forward) 1 else 0,
                "speed" to speed
            )
            post("/cmd/move", gson.toJson(body))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun turn(angle: Int, left: Boolean = true, speed: Double = 0.6): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "direction" to if (left) 1 else 0,
                "angle" to angle,
                "speed" to speed
            )
            post("/cmd/turn", gson.toJson(body))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setSpeed(vx: Double, vth: Double): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf("vx" to vx, "vth" to vth)
            post("/cmd/speed", gson.toJson(body))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stop(): Result<Boolean> = setSpeed(0.0, 0.0)

    // Map
    suspend fun getMap(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = get("/reeman/map")
            val json = JsonParser.parseString(response).asJsonObject
            val imageUrl = json.get("image_url")?.asString ?: ""
            Result.success(imageUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Health check
    suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            get("/reeman/pose")
            true
        } catch (e: Exception) {
            false
        }
    }

    // HTTP helpers
    private fun get(endpoint: String): String {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }

    private fun post(endpoint: String, json: String): String {
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }
}
