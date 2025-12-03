package com.docent.bot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.docent.bot.R
import com.docent.bot.model.ProjectorDevice
import com.docent.bot.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

class DLNADiscoveryService : Service() {

    companion object {
        private const val TAG = "DLNADiscoveryService"
        private const val CHANNEL_ID = "dlna_discovery_channel"
        private const val NOTIFICATION_ID = 1001

        // mDNS service types to search for (only RAOP - EShare devices advertise via RAOP)
        private val MDNS_SERVICE_TYPES = listOf(
            "_raop._tcp.local."  // EShare devices advertise via RAOP
        )

        // Disable port scanning - only mDNS discovered EShare devices work reliably
        private val COMMON_PORTS = emptyList<Int>()
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null

    private val _devices = MutableStateFlow<List<ProjectorDevice>>(emptyList())
    val devices: StateFlow<List<ProjectorDevice>> = _devices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val discoveredDevices = mutableMapOf<String, ProjectorDevice>()

    inner class LocalBinder : Binder() {
        fun getService(): DLNADiscoveryService = this@DLNADiscoveryService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DLNADiscoveryService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireMulticastLock()
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DLNADiscoveryService destroyed")
        stopDiscovery()
        releaseMulticastLock()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "프로젝터 검색",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "프로젝터 검색 서비스"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("도슨트봇")
            .setContentText("프로젝터 검색 중...")
            .setSmallIcon(R.drawable.ic_projector)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireMulticastLock() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("DocentBot::MulticastLock")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()
        Log.d(TAG, "Multicast lock acquired")
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Multicast lock released")
            }
        }
        multicastLock = null
    }

    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.d(TAG, "Discovery already in progress")
            return
        }

        _isDiscovering.value = true
        discoveredDevices.clear()
        _devices.value = emptyList()

        Log.d(TAG, "Starting discovery...")

        serviceScope.launch {
            // Start mDNS discovery using JmDNS
            launch { startJmDNSDiscovery() }

            // Start EShare UDP discovery
            launch { startEShareUDPDiscovery() }

            // Start network scanning
            launch { startNetworkScan() }

            // Stop discovery indicator after 15 seconds
            delay(15000)
            withContext(Dispatchers.Main) {
                _isDiscovering.value = false
            }
        }
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        stopJmDNS()
        Log.d(TAG, "Discovery stopped")
    }

    /**
     * Start mDNS discovery using JmDNS library
     */
    private suspend fun startJmDNSDiscovery() {
        withContext(Dispatchers.IO) {
            try {
                val localIp = NetworkUtils.getLocalIpAddress(this@DLNADiscoveryService)
                if (localIp == null) {
                    Log.e(TAG, "Cannot get local IP address")
                    return@withContext
                }

                Log.d(TAG, "Starting JmDNS on $localIp")
                val inetAddress = InetAddress.getByName(localIp)
                jmdns = JmDNS.create(inetAddress, "DocentBot")

                val serviceListener = object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        Log.d(TAG, "mDNS service added: ${event.name} (${event.type})")
                        // Request more info about the service
                        jmdns?.requestServiceInfo(event.type, event.name, 5000)
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        Log.d(TAG, "mDNS service removed: ${event.name}")
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info
                        val host = info.hostAddresses.firstOrNull()
                        val name = info.name

                        Log.d(TAG, "mDNS service resolved: $name (${event.type}) at $host:${info.port}")

                        // Only accept EShare devices (they contain "EShare" in the name)
                        if (!name.contains("EShare", ignoreCase = true)) {
                            Log.d(TAG, "Skipping non-EShare device: $name")
                            return
                        }

                        if (host != null) {
                            // EShare devices use port 7000 for AirPlay video streaming
                            val videoPort = 7000

                            // Clean up device name (remove MAC address prefix if present)
                            val cleanName = if (name.contains("@")) {
                                name.substringAfter("@")
                            } else {
                                name
                            }

                            Log.d(TAG, "Adding EShare device: $cleanName at $host:$videoPort")

                            addDevice(ProjectorDevice(
                                name = cleanName,
                                host = host,
                                port = videoPort,
                                type = "EShare"
                            ), priority = true)
                        }
                    }
                }

                // Register listeners for all mDNS service types
                MDNS_SERVICE_TYPES.forEach { serviceType ->
                    try {
                        jmdns?.addServiceListener(serviceType, serviceListener)
                        Log.d(TAG, "Listening for mDNS type: $serviceType")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding listener for $serviceType", e)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error starting JmDNS discovery", e)
            }
        }
    }

    private fun stopJmDNS() {
        try {
            jmdns?.close()
            jmdns = null
            Log.d(TAG, "JmDNS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping JmDNS", e)
        }
    }

    /**
     * Send EShare-specific UDP discovery
     */
    private suspend fun startEShareUDPDiscovery() {
        val discoveryPorts = listOf(48689, 8121, 2425)

        withContext(Dispatchers.IO) {
            discoveryPorts.forEach { port ->
                try {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        socket.soTimeout = 3000

                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val message = "ESHARE_DISCOVER".toByteArray()
                        val packet = DatagramPacket(message, message.size, broadcastAddr, port)

                        socket.send(packet)
                        Log.d(TAG, "Sent EShare discovery on port $port")

                        // Try to receive responses
                        val buffer = ByteArray(1024)
                        val responsePacket = DatagramPacket(buffer, buffer.size)

                        repeat(3) {
                            try {
                                socket.receive(responsePacket)
                                val responderHost = responsePacket.address.hostAddress
                                val response = String(responsePacket.data, 0, responsePacket.length)

                                Log.d(TAG, "EShare response from $responderHost: $response")

                                if (responderHost != null) {
                                    addDevice(ProjectorDevice(
                                        name = "EShare Device",
                                        host = responderHost,
                                        port = 7000,  // Default EShare video port
                                        type = "EShare"
                                    ))
                                }
                            } catch (e: Exception) {
                                // Timeout, no more responses
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending EShare discovery on port $port", e)
                }
            }
        }
    }

    /**
     * Scan network for devices on common ports
     */
    private suspend fun startNetworkScan() {
        withContext(Dispatchers.IO) {
            try {
                val localIp = NetworkUtils.getLocalIpAddress(this@DLNADiscoveryService)
                    ?: return@withContext
                val subnet = localIp.substringBeforeLast(".")

                Log.d(TAG, "Starting network scan on subnet: $subnet.*")

                // Scan common IP range (1-50 for speed)
                (1..50).map { i ->
                    async {
                        val host = "$subnet.$i"
                        if (host == localIp) return@async  // Skip our IP

                        COMMON_PORTS.forEach { port ->
                            if (isPortOpen(host, port, 200)) {
                                val deviceType = identifyDeviceType(host, port)
                                if (deviceType != null) {
                                    addDevice(ProjectorDevice(
                                        name = "$deviceType ($host)",
                                        host = host,
                                        port = port,
                                        type = deviceType
                                    ))
                                }
                            }
                        }
                    }
                }.awaitAll()

            } catch (e: Exception) {
                Log.e(TAG, "Error during network scan", e)
            }
        }
    }

    private fun isPortOpen(host: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun identifyDeviceType(host: String, port: Int): String? {
        return when (port) {
            7000, 7100 -> "AirPlay"
            8008, 8009 -> "Chromecast"
            else -> null
        }
    }

    /**
     * Add device to discovered list
     */
    private fun addDevice(device: ProjectorDevice, priority: Boolean = false) {
        synchronized(discoveredDevices) {
            val existingDevice = discoveredDevices[device.host]

            if (existingDevice == null) {
                // New device
                discoveredDevices[device.host] = device
                _devices.value = discoveredDevices.values.toList()
                Log.d(TAG, "Device added: ${device.name} at ${device.host}:${device.port}")
            } else if (priority && existingDevice.type == "Network Device") {
                // Replace network-scanned device with mDNS discovery result
                discoveredDevices[device.host] = device
                _devices.value = discoveredDevices.values.toList()
                Log.d(TAG, "Device updated (mDNS priority): ${device.name}")
            } else {
                // Keep existing device
            }
        }
    }

    fun selectDevice(device: ProjectorDevice) {
        synchronized(discoveredDevices) {
            discoveredDevices.values.forEach { it.isSelected = false }
            discoveredDevices[device.host]?.isSelected = true
            _devices.value = discoveredDevices.values.toList()
        }
        Log.d(TAG, "Device selected: ${device.name}")
    }

    fun getSelectedDevice(): ProjectorDevice? {
        return discoveredDevices.values.find { it.isSelected }
    }
}
