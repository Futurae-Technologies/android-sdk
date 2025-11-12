package com.futurae.futuraedemo.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.qr_push_action.QRCodeRequestedActionHandler
import com.futurae.futuraedemo.ui.qr_push_action.QRCodeRequestedActionListener
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.utils.FTNotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class App : Application() {

    companion object {
        private const val CHANNEL_ID = "FuturaeChannelId"
        private const val CHANNEL_NAME = "General Notifications"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val intentFilter = IntentFilter().apply {
        addAction(FTNotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)
        addAction(FTNotificationUtils.INTENT_APPROVE_AUTH_MESSAGE)
        addAction(FTNotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR)
        addAction(FTNotificationUtils.INTENT_APPROVE_CANCEL_MESSAGE)
        addAction(FTNotificationUtils.INTENT_CUSTOM_NOTIFICATION)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("FuturaeNotificationDebug", "broadcastReceiver onReceive")
            showSystemNotification(intent.action ?: "Missing action")
        }
    }

    private val qrCodeActionListener: QRCodeRequestedActionListener by lazy {
        object : QRCodeRequestedActionListener() {
            override fun onQRCodeScanRequested() {
                QRCodeRequestedActionHandler.handle(this@App)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver,
            intentFilter
        )

        Timber.plant(Timber.DebugTree())
        applicationScope.launch {
            FuturaeSDK.sdkState().collect{
                Timber.i("SDK state: $it")
            }
        }

        qrCodeActionListener.startListening(this)
    }

    private fun showSystemNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel(notificationManager)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Futurae notification received")
            .setContentText(message)
            .setAutoCancel(true)

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for high priority push notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}