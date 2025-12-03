package com.docent.bot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DocentApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // DLNA Discovery Channel
            NotificationChannel(
                "dlna_discovery_channel",
                "프로젝터 검색",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "프로젝터 검색 서비스 알림"
                notificationManager.createNotificationChannel(this)
            }

            // Media Server Channel
            NotificationChannel(
                "media_server_channel",
                "미디어 서버",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "동영상 스트리밍 서비스 알림"
                notificationManager.createNotificationChannel(this)
            }

            // General Channel
            NotificationChannel(
                "docent_channel",
                "도슨트봇",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "도슨트봇 일반 알림"
                notificationManager.createNotificationChannel(this)
            }
        }
    }
}
