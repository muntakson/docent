package com.docent.bot.model

data class RobotPose(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val theta: Double = 0.0
)

data class NavStatus(
    val res: Int = 6,  // 1=moving, 3=arrived, 4=cancelled, 6=idle
    val reason: Int = 0,
    val goal: String = "",
    val dist: Double = 0.0,
    val mileage: Double = 0.0
) {
    val isMoving: Boolean get() = res == 1
    val isArrived: Boolean get() = res == 3
    val isCancelled: Boolean get() = res == 4
    val isIdle: Boolean get() = res == 6
}

data class BaseEncode(
    val battery: Int = 0,
    val chargeFlag: Int = 1,  // 1=not charging, 2=charging
    val emergencyButton: Int = 1  // 0=pressed, 1=released
) {
    val isCharging: Boolean get() = chargeFlag == 2
    val isEmergencyPressed: Boolean get() = emergencyButton == 0
}

data class Waypoint(
    val name: String,
    val type: String = "normal",  // delivery, charge, normal
    val pose: RobotPose
)

data class Zone(
    val id: String,
    val name: String,
    val pointName: String,
    val speech: String
)

data class RoutePoint(
    val name: String,
    val stayTime: Int = 10
)

data class Route(
    val name: String,
    val points: List<RoutePoint>,
    val createdAt: String = ""
)

enum class AppState {
    IDLE,           // Waiting for user input
    CHECKING_POSITION,  // Checking if at welcome zone
    WELCOME,        // Showing welcome + playing video
    WAITING_CONTINUE,   // Waiting for "안내시작" button
    NAVIGATING,     // Moving to next waypoint
    SPEAKING,       // Playing TTS
    FINISHED        // Tour completed
}

data class DocentState(
    val appState: AppState = AppState.IDLE,
    val robotPose: RobotPose = RobotPose(),
    val navStatus: NavStatus = NavStatus(),
    val baseEncode: BaseEncode = BaseEncode(),
    val waypoints: List<Waypoint> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val currentRoute: Route? = null,
    val currentRouteIndex: Int = 0,
    val currentZone: Zone? = null,
    val isConnected: Boolean = false,
    val statusMessage: String = ""
)
