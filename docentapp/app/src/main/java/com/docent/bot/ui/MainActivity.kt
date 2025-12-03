package com.docent.bot.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.docent.bot.databinding.ActivityMainBinding
import com.docent.bot.model.AppState
import com.docent.bot.model.StreamingState
import com.docent.bot.service.DLNADiscoveryService
import com.docent.bot.service.MediaServerService
import com.docent.bot.util.PreferenceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var dlnaService: DLNADiscoveryService? = null
    private var mediaServerService: MediaServerService? = null

    private val prefs by lazy { PreferenceManager(this) }

    private val dlnaServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            dlnaService = (service as DLNADiscoveryService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dlnaService = null
        }
    }

    private val mediaServerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mediaServerService = (service as MediaServerService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaServerService = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFullscreen()
        setupViews()
        observeState()
        requestPermissions()
        bindServices()
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateApiServices()
        viewModel.startStatusPolling()
        binding.leftEye.startIdleAnimation()
        binding.rightEye.startIdleAnimation()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopStatusPolling()
        binding.leftEye.stopIdleAnimation()
        binding.rightEye.stopIdleAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindServices()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun setupViews() {
        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Start button
        binding.btnStart.setOnClickListener {
            viewModel.onStartClicked()
        }

        // Continue button
        binding.btnContinue.setOnClickListener {
            viewModel.onContinueClicked()
        }

        // Stop button
        binding.btnStop.setOnClickListener {
            viewModel.stopNavigation()
            viewModel.resetToIdle()
        }

        // Initialize mouth to closed state
        binding.mouth.setState(MouthView.MouthState.CLOSED, animate = false)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collectLatest { state ->
                        updateUI(state.appState)
                        updateStatus(state)
                    }
                }

                launch {
                    viewModel.speechText.collectLatest { text ->
                        if (text != null) {
                            showSpeechText(text)
                            setSpeaking(true)
                        } else {
                            hideSpeechText()
                            setSpeaking(false)
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(appState: AppState) {
        when (appState) {
            AppState.IDLE -> {
                binding.btnStart.visibility = View.VISIBLE
                binding.btnContinue.visibility = View.GONE
                binding.btnStop.visibility = View.GONE
                binding.loadingOverlay.visibility = View.GONE
                binding.mouth.setState(MouthView.MouthState.CLOSED)
            }

            AppState.CHECKING_POSITION -> {
                binding.btnStart.visibility = View.GONE
                binding.btnContinue.visibility = View.GONE
                binding.btnStop.visibility = View.GONE
                binding.loadingOverlay.visibility = View.VISIBLE
            }

            AppState.WELCOME -> {
                binding.btnStart.visibility = View.GONE
                binding.btnContinue.visibility = View.GONE
                binding.btnStop.visibility = View.VISIBLE
                binding.loadingOverlay.visibility = View.GONE
                binding.mouth.setState(MouthView.MouthState.SMILING)

                // Check if we should play video
                if (prefs.hasProjector() && prefs.videoUri != null) {
                    playWelcomeVideo()
                } else {
                    // No video, go directly to waiting for continue
                    viewModel.onVideoFinished()
                }
            }

            AppState.WAITING_CONTINUE -> {
                binding.btnStart.visibility = View.GONE
                binding.btnContinue.visibility = View.VISIBLE
                binding.btnStop.visibility = View.GONE
                binding.loadingOverlay.visibility = View.GONE
                binding.mouth.setState(MouthView.MouthState.SMILING)
            }

            AppState.NAVIGATING -> {
                binding.btnStart.visibility = View.GONE
                binding.btnContinue.visibility = View.GONE
                binding.btnStop.visibility = View.VISIBLE
                binding.loadingOverlay.visibility = View.GONE
                binding.mouth.setState(MouthView.MouthState.CLOSED)
            }

            AppState.SPEAKING -> {
                binding.btnStart.visibility = View.GONE
                binding.btnContinue.visibility = View.GONE
                binding.btnStop.visibility = View.VISIBLE
                binding.loadingOverlay.visibility = View.GONE
                binding.mouth.setState(MouthView.MouthState.SPEAKING)
            }

            AppState.FINISHED -> {
                binding.btnStart.visibility = View.GONE
                binding.btnContinue.visibility = View.GONE
                binding.btnStop.visibility = View.GONE
                binding.loadingOverlay.visibility = View.GONE
                binding.mouth.setState(MouthView.MouthState.SMILING)
            }
        }
    }

    private fun updateStatus(state: com.docent.bot.model.DocentState) {
        val statusText = when (state.appState) {
            AppState.IDLE -> getString(com.docent.bot.R.string.status_idle)
            AppState.CHECKING_POSITION -> "위치 확인 중"
            AppState.WELCOME -> getString(com.docent.bot.R.string.status_welcome)
            AppState.WAITING_CONTINUE -> "대기 중"
            AppState.NAVIGATING -> getString(com.docent.bot.R.string.status_navigating)
            AppState.SPEAKING -> getString(com.docent.bot.R.string.status_speaking)
            AppState.FINISHED -> "완료"
        }

        val statusColor = when {
            !state.isConnected -> ContextCompat.getColor(this, com.docent.bot.R.color.status_error)
            state.appState == AppState.IDLE -> ContextCompat.getColor(this, com.docent.bot.R.color.status_idle)
            else -> ContextCompat.getColor(this, com.docent.bot.R.color.status_active)
        }

        binding.tvStatus.text = statusText
        binding.tvStatus.setTextColor(statusColor)
    }

    private fun showSpeechText(text: String) {
        binding.tvSpeechText.text = text
        binding.tvSpeechText.visibility = View.VISIBLE
    }

    private fun hideSpeechText() {
        binding.tvSpeechText.visibility = View.GONE
    }

    private fun setSpeaking(speaking: Boolean) {
        binding.leftEye.setSpeaking(speaking)
        binding.rightEye.setSpeaking(speaking)

        if (speaking) {
            binding.mouth.setState(MouthView.MouthState.SPEAKING)
        } else {
            binding.mouth.setState(MouthView.MouthState.SMILING)
        }
    }

    private fun playWelcomeVideo() {
        val videoUri = prefs.videoUri ?: return

        // Start streaming to projector
        mediaServerService?.let { service ->
            val device = com.docent.bot.model.ProjectorDevice(
                name = prefs.projectorName ?: "Projector",
                host = prefs.projectorHost ?: return,
                port = prefs.projectorPort,
                type = prefs.projectorType ?: "EShare"
            )

            // Set video file
            try {
                val uri = android.net.Uri.parse(videoUri)
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val name = if (nameIndex >= 0) it.getString(nameIndex) else "video.mp4"
                        val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L

                        val videoFile = com.docent.bot.model.VideoFile(
                            uri = uri,
                            name = name,
                            mimeType = contentResolver.getType(uri) ?: "video/mp4",
                            size = size
                        )

                        service.setVideoFile(videoFile)
                        service.startStreaming(device)

                        // Monitor streaming state
                        lifecycleScope.launch {
                            service.streamingState.collectLatest { streamState ->
                                when (streamState) {
                                    is StreamingState.Streaming -> {
                                        binding.tvStatus.text = getString(com.docent.bot.R.string.status_playing_video)
                                    }
                                    is StreamingState.Stopped, is StreamingState.Error -> {
                                        viewModel.onVideoFinished()
                                    }
                                    else -> {}
                                }
                            }
                        }

                        // Set timeout for video (e.g., 2 minutes max)
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(120000) // 2 minutes
                            if (viewModel.state.value.appState == AppState.WELCOME) {
                                service.stopStreaming()
                                viewModel.onVideoFinished()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                viewModel.onVideoFinished()
            }
        } ?: run {
            // No media server, skip video
            viewModel.onVideoFinished()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun bindServices() {
        // Bind DLNA Discovery Service
        Intent(this, DLNADiscoveryService::class.java).also { intent ->
            bindService(intent, dlnaServiceConnection, Context.BIND_AUTO_CREATE)
        }

        // Bind Media Server Service
        Intent(this, MediaServerService::class.java).also { intent ->
            bindService(intent, mediaServerConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindServices() {
        try {
            unbindService(dlnaServiceConnection)
        } catch (e: Exception) { }

        try {
            unbindService(mediaServerConnection)
        } catch (e: Exception) { }
    }
}
