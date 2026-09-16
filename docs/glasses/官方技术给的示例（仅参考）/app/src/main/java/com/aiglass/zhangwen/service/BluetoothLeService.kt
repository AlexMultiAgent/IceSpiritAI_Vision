package com.aiglass.zhangwen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aiglass.zhangwen.MainActivity
import com.aiglass.zhangwen.R
import com.aiglass.zhangwen.bluetooth.BluetoothController
import com.aiglass.zhangwen.bluetooth.GlassesBluetooth

class BluetoothLeService : Service() {
    private val binder = LocalBinder()
    private lateinit var bluetoothController: BluetoothController

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
        fun getBluetoothController(): BluetoothController = bluetoothController
    }

    override fun onCreate() {
        super.onCreate()
        GlassesBluetooth.init(applicationContext)
        bluetoothController = GlassesBluetooth.get()
        startAsForeground()
        bluetoothController.scheduleStartupAutoReconnect()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "gov_glasses_bt"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.bt_service_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.bt_service_notification))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(1001, notification)
        }
    }
}
