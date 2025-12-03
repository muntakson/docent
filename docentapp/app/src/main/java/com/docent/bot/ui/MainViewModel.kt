package com.docent.bot.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docent.bot.model.*
import com.docent.bot.service.AMRApiService
import com.docent.bot.service.BackendApiService
import com.docent.bot.util.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private var amrApi: AMRApiService = AMRApiService(prefs.amrIp)
    private var backendApi: BackendApiService = BackendApiService(prefs.backendUrl)

    private val _state = MutableStateFlow(DocentState())
    val state: StateFlow<DocentState> = _state.asStateFlow()

    private val _speechText = MutableStateFlow<String?>(null)
    val speechText: StateFlow<String?> = _speechText.asStateFlow()

    private var statusPollingJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private var currentRoute: Route? = null
    private var currentRouteIndex: Int = 0

    init {
        loadInitialData()
    }

    fun updateApiServices() {
        amrApi = AMRApiService(prefs.amrIp)
        backendApi = BackendApiService(prefs.backendUrl)
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Load zones
            backendApi.getZones().onSuccess { zones ->
                _state.value = _state.value.copy(zones = zones)
            }

            // Load waypoints from AMR
            amrApi.getWaypoints().onSuccess { waypoints ->
                _state.value = _state.value.copy(waypoints = waypoints)
            }

            // Check connection
            val isConnected = amrApi.isConnected()
            _state.value = _state.value.copy(isConnected = isConnected)

            if (isConnected) {
                startStatusPolling()
            }
        }
    }

    fun startStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    // Get robot pose
                    amrApi.getPose().onSuccess { pose ->
                        _state.value = _state.value.copy(robotPose = pose)
                    }

                    // Get nav status
                    amrApi.getNavStatus().onSuccess { navStatus ->
                        val previousStatus = _state.value.navStatus
                        _state.value = _state.value.copy(navStatus = navStatus)

                        // Check if arrived at destination
                        if (navStatus.isArrived && !previousStatus.isArrived) {
                            onArrivedAtWaypoint(navStatus.goal)
                        }
                    }

                    // Get base encode (battery, etc.)
                    amrApi.getBaseEncode().onSuccess { baseEncode ->
                        _state.value = _state.value.copy(baseEncode = baseEncode)
                    }

                    _state.value = _state.value.copy(isConnected = true)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(isConnected = false)
                }

                delay(1000)
            }
        }
    }

    fun stopStatusPolling() {
        statusPollingJob?.cancel()
    }

    // Called when user clicks Start button
    fun onStartClicked() {
        viewModelScope.launch {
            _state.value = _state.value.copy(appState = AppState.CHECKING_POSITION)

            // Check if at welcome zone
            val currentPose = _state.value.robotPose
            val welcomeZone = _state.value.zones.find { it.name.contains("웰컴") || it.name.contains("welcome", ignoreCase = true) }

            // For now, assume we're at welcome zone and start the welcome sequence
            startWelcomeSequence(welcomeZone)
        }
    }

    private fun startWelcomeSequence(welcomeZone: Zone?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                appState = AppState.WELCOME,
                currentZone = welcomeZone
            )

            // Speak welcome message
            val welcomeSpeech = welcomeZone?.speech ?: "안녕! 나는 서희봇이야! 만나서 반가워!"
            speak(welcomeSpeech)
        }
    }

    suspend fun speak(text: String) {
        _state.value = _state.value.copy(appState = AppState.SPEAKING)
        _speechText.value = text

        try {
            val result = backendApi.synthesizeSpeech(
                text = text,
                voice = prefs.ttsVoice,
                rate = prefs.ttsRate
            )

            result.onSuccess { audioData ->
                playAudio(audioData)
            }.onFailure {
                // Fallback: just show text
                delay(text.length * 100L)
            }
        } catch (e: Exception) {
            delay(text.length * 100L)
        }

        _speechText.value = null
    }

    private suspend fun playAudio(audioData: ByteArray) {
        return suspendCancellableCoroutine { continuation ->
            try {
                // Save to temp file
                val tempFile = File.createTempFile("tts_", ".mp3", getApplication<Application>().cacheDir)
                FileOutputStream(tempFile).use { it.write(audioData) }

                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    prepare()

                    setOnCompletionListener {
                        tempFile.delete()
                        if (continuation.isActive) {
                            continuation.resume(Unit) {}
                        }
                    }

                    setOnErrorListener { _, _, _ ->
                        tempFile.delete()
                        if (continuation.isActive) {
                            continuation.resume(Unit) {}
                        }
                        true
                    }

                    start()
                }

                continuation.invokeOnCancellation {
                    mediaPlayer?.release()
                    tempFile.delete()
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Unit) {}
                }
            }
        }
    }

    // Called after welcome video finishes - show continue button
    fun onVideoFinished() {
        _state.value = _state.value.copy(appState = AppState.WAITING_CONTINUE)
    }

    // Called when user clicks Continue (안내시작) button
    fun onContinueClicked() {
        viewModelScope.launch {
            _state.value = _state.value.copy(appState = AppState.NAVIGATING)

            // Start navigation to first point in route
            // For now, navigate through all zones in order
            startTour()
        }
    }

    private fun startTour() {
        viewModelScope.launch {
            val zones = _state.value.zones.filter { it.pointName.isNotEmpty() }

            for (zone in zones.drop(1)) { // Skip welcome zone
                // Navigate to zone waypoint
                navigateTo(zone.pointName)

                // Wait for arrival
                waitForArrival()

                // Speak zone speech
                _state.value = _state.value.copy(currentZone = zone)
                speak(zone.speech)

                delay(2000) // Brief pause between zones
            }

            // Tour finished
            _state.value = _state.value.copy(appState = AppState.FINISHED)
            speak("오늘 투어가 끝났어요! 다음에 또 만나!")

            delay(3000)
            _state.value = _state.value.copy(appState = AppState.IDLE)
        }
    }

    fun navigateTo(pointName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                appState = AppState.NAVIGATING,
                statusMessage = "$pointName(으)로 이동 중..."
            )
            amrApi.navigateTo(pointName)
        }
    }

    private suspend fun waitForArrival() {
        while (_state.value.navStatus.res != 3) { // 3 = arrived
            delay(500)
        }
    }

    private fun onArrivedAtWaypoint(waypointName: String) {
        viewModelScope.launch {
            val zone = _state.value.zones.find { it.pointName == waypointName }

            if (zone != null) {
                _state.value = _state.value.copy(currentZone = zone)
                speak(zone.speech)
            } else {
                speak("${waypointName}에 도착했어요")
            }
        }
    }

    fun stopNavigation() {
        viewModelScope.launch {
            amrApi.cancelNavigation()
            amrApi.stop()
            _state.value = _state.value.copy(
                appState = AppState.IDLE,
                statusMessage = "정지됨"
            )
        }
    }

    fun resetToIdle() {
        mediaPlayer?.release()
        mediaPlayer = null
        _speechText.value = null
        _state.value = _state.value.copy(
            appState = AppState.IDLE,
            currentZone = null,
            statusMessage = ""
        )
    }

    override fun onCleared() {
        super.onCleared()
        statusPollingJob?.cancel()
        mediaPlayer?.release()
    }
}
