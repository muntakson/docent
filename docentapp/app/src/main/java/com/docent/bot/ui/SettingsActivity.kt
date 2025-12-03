package com.docent.bot.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.docent.bot.R
import com.docent.bot.databinding.ActivitySettingsBinding
import com.docent.bot.model.ProjectorDevice
import com.docent.bot.model.StreamingState
import com.docent.bot.model.VideoFile
import com.docent.bot.service.AMRApiService
import com.docent.bot.service.DLNADiscoveryService
import com.docent.bot.service.MediaServerService
import com.docent.bot.util.PreferenceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferenceManager

    private var dlnaService: DLNADiscoveryService? = null
    private var mediaServerService: MediaServerService? = null
    private var deviceAdapter: DeviceAdapter? = null

    private var selectedDevice: ProjectorDevice? = null
    private var selectedVideoFile: VideoFile? = null

    private val dlnaServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            dlnaService = (service as DLNADiscoveryService.LocalBinder).getService()
            observeDevices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dlnaService = null
        }
    }

    private val mediaServerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mediaServerService = (service as MediaServerService.LocalBinder).getService()
            observeStreamingState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaServerService = null
        }
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleVideoSelection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager(this)

        setupViews()
        loadSavedSettings()
        bindServices()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindServices()
    }

    private fun setupViews() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // AMR IP
        binding.etAmrIp.setText(prefs.amrIp)
        binding.etBackendUrl.setText(prefs.backendUrl)

        // Test connection button
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        // Find projector button
        binding.btnFindProjector.setOnClickListener {
            startProjectorDiscovery()
        }

        // Video upload button
        binding.btnVideoUpload.setOnClickListener {
            openVideoPicker()
        }

        // Stream video button
        binding.btnStreamVideo.setOnClickListener {
            startStreaming()
        }

        // Stop stream button
        binding.btnStopStream.setOnClickListener {
            stopStreaming()
        }

        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        // Setup device list
        deviceAdapter = DeviceAdapter { device ->
            selectDevice(device)
        }
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = deviceAdapter
        }
    }

    private fun loadSavedSettings() {
        // Load saved projector
        if (prefs.hasProjector()) {
            selectedDevice = ProjectorDevice(
                name = prefs.projectorName ?: "Projector",
                host = prefs.projectorHost!!,
                port = prefs.projectorPort,
                type = prefs.projectorType ?: "EShare",
                isSelected = true
            )
            binding.tvSelectedProjector.text = getString(R.string.projector_found, selectedDevice?.displayName)
        }

        // Load saved video
        prefs.videoUri?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                binding.tvSelectedVideo.text = getString(R.string.video_selected, prefs.videoName ?: "video")
                selectedVideoFile = VideoFile(
                    uri = uri,
                    name = prefs.videoName ?: "video",
                    mimeType = "video/mp4",
                    size = 0
                )
            } catch (e: Exception) {
                prefs.videoUri = null
            }
        }

        updateStreamButtonState()
    }

    private fun testConnection() {
        val amrIp = binding.etAmrIp.text.toString().trim()
        if (amrIp.isEmpty()) {
            Toast.makeText(this, "AMR IP를 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvAmrStatus.text = "연결 확인 중..."
        updateStatusDot(null)

        lifecycleScope.launch {
            val api = AMRApiService(amrIp)
            val isConnected = api.isConnected()

            if (isConnected) {
                binding.tvAmrStatus.text = "AMR 연결됨"
                updateStatusDot(true)
            } else {
                binding.tvAmrStatus.text = "AMR 연결 실패"
                updateStatusDot(false)
            }
        }
    }

    private fun updateStatusDot(connected: Boolean?) {
        val color = when (connected) {
            true -> ContextCompat.getColor(this, R.color.status_active)
            false -> ContextCompat.getColor(this, R.color.status_error)
            null -> ContextCompat.getColor(this, R.color.status_idle)
        }
        (binding.viewAmrStatus.background as? GradientDrawable)?.setColor(color)
            ?: run {
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                drawable.setColor(color)
                binding.viewAmrStatus.background = drawable
            }
    }

    private fun startProjectorDiscovery() {
        binding.btnFindProjector.text = "검색 중..."
        binding.btnFindProjector.isEnabled = false
        binding.tvDeviceListTitle.visibility = View.VISIBLE
        binding.rvDevices.visibility = View.VISIBLE

        dlnaService?.startDiscovery()

        // Re-enable button after discovery timeout
        lifecycleScope.launch {
            kotlinx.coroutines.delay(16000)
            binding.btnFindProjector.text = getString(R.string.find_projector)
            binding.btnFindProjector.isEnabled = true
        }
    }

    private fun observeDevices() {
        lifecycleScope.launch {
            dlnaService?.devices?.collectLatest { devices ->
                deviceAdapter?.submitList(devices.toList())

                if (devices.isEmpty()) {
                    binding.tvDeviceListTitle.text = "프로젝터를 찾지 못했습니다"
                } else {
                    binding.tvDeviceListTitle.text = "발견된 프로젝터 (${devices.size})"
                }
            }
        }

        lifecycleScope.launch {
            dlnaService?.isDiscovering?.collectLatest { isDiscovering ->
                if (!isDiscovering) {
                    binding.btnFindProjector.text = getString(R.string.find_projector)
                    binding.btnFindProjector.isEnabled = true
                }
            }
        }
    }

    private fun selectDevice(device: ProjectorDevice) {
        // Deselect previous
        selectedDevice?.isSelected = false

        // Select new
        device.isSelected = true
        selectedDevice = device

        dlnaService?.selectDevice(device)

        binding.tvSelectedProjector.text = getString(R.string.projector_found, device.displayName)
        updateStreamButtonState()

        Toast.makeText(this, getString(R.string.toast_projector_connected), Toast.LENGTH_SHORT).show()
    }

    private fun openVideoPicker() {
        videoPickerLauncher.launch(arrayOf("video/*"))
    }

    private fun handleVideoSelection(uri: Uri) {
        try {
            // Take persistable permission
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // Get file info
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)

                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "video"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    val mimeType = contentResolver.getType(uri) ?: "video/mp4"

                    selectedVideoFile = VideoFile(
                        uri = uri,
                        name = name,
                        mimeType = mimeType,
                        size = size
                    )

                    binding.tvSelectedVideo.text = getString(R.string.video_selected, name)
                    updateStreamButtonState()

                    Toast.makeText(this, getString(R.string.toast_video_selected), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "동영상을 선택할 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStreamButtonState() {
        val canStream = selectedDevice != null && selectedVideoFile != null
        binding.btnStreamVideo.isEnabled = canStream
    }

    private fun startStreaming() {
        val device = selectedDevice ?: return
        val videoFile = selectedVideoFile ?: return

        mediaServerService?.let { service ->
            service.setVideoFile(videoFile)
            service.startStreaming(device)
        }
    }

    private fun stopStreaming() {
        mediaServerService?.stopStreaming()
    }

    private fun observeStreamingState() {
        lifecycleScope.launch {
            mediaServerService?.streamingState?.collectLatest { state ->
                when (state) {
                    is StreamingState.Idle -> {
                        binding.btnStreamVideo.visibility = View.VISIBLE
                        binding.btnStopStream.visibility = View.GONE
                        binding.tvStreamingStatus.visibility = View.GONE
                    }
                    is StreamingState.Preparing -> {
                        binding.btnStreamVideo.visibility = View.GONE
                        binding.btnStopStream.visibility = View.VISIBLE
                        binding.tvStreamingStatus.visibility = View.VISIBLE
                        binding.tvStreamingStatus.text = "연결 중..."
                    }
                    is StreamingState.Streaming -> {
                        binding.btnStreamVideo.visibility = View.GONE
                        binding.btnStopStream.visibility = View.VISIBLE
                        binding.tvStreamingStatus.visibility = View.VISIBLE
                        binding.tvStreamingStatus.text = getString(R.string.streaming_to, state.deviceName)
                        Toast.makeText(this@SettingsActivity, getString(R.string.toast_streaming_started), Toast.LENGTH_SHORT).show()
                    }
                    is StreamingState.Stopped -> {
                        binding.btnStreamVideo.visibility = View.VISIBLE
                        binding.btnStopStream.visibility = View.GONE
                        binding.tvStreamingStatus.visibility = View.GONE
                        Toast.makeText(this@SettingsActivity, getString(R.string.toast_streaming_stopped), Toast.LENGTH_SHORT).show()
                    }
                    is StreamingState.Error -> {
                        binding.btnStreamVideo.visibility = View.VISIBLE
                        binding.btnStopStream.visibility = View.GONE
                        binding.tvStreamingStatus.visibility = View.VISIBLE
                        binding.tvStreamingStatus.text = state.message
                        binding.tvStreamingStatus.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.status_error))
                    }
                    else -> {}
                }
            }
        }
    }

    private fun saveSettings() {
        val amrIp = binding.etAmrIp.text.toString().trim()
        val backendUrl = binding.etBackendUrl.text.toString().trim()

        if (amrIp.isEmpty()) {
            Toast.makeText(this, "AMR IP를 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }

        if (backendUrl.isEmpty()) {
            Toast.makeText(this, "백엔드 URL을 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }

        // Save AMR settings
        prefs.amrIp = amrIp
        prefs.backendUrl = backendUrl

        // Save projector settings
        selectedDevice?.let { device ->
            prefs.saveProjector(device.host, device.port, device.type, device.displayName)
        }

        // Save video settings
        selectedVideoFile?.let { video ->
            prefs.videoUri = video.uri.toString()
            prefs.videoName = video.name
        }

        Toast.makeText(this, "설정이 저장되었습니다", Toast.LENGTH_SHORT).show()
        finish()
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
