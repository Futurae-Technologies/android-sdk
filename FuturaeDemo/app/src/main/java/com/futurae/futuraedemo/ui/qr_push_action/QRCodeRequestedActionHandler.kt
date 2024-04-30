package com.futurae.futuraedemo.ui.qr_push_action

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.activity.MainActivity


/**
 * This is a client-app-specific handling for the QR_CODE action, sent by the SDK.
 * From here, client could spawn local notification or do whatever needs to be done to handle this signal.
 *
 * It is up to to the client to implement handling of the QR_CODE action when reported by the SDK.
 * All constants are up to the host app.
 */
object QRCodeRequestedActionHandler {

    const val CLIENT_APP_QR_ACTION_BOOLEAN_EXTRA = "qr_code"
    private const val CLIENT_APP_PENDING_INTENT_REQUEST_CODE = 101
    private const val CLIENT_APP_NOTIFICATION_CHANNEL_ID = "qr_code"
    private const val CLIENT_APP_NOTIFICATION_CHANNEL_NAME = "qr_code_channel"
    private const val CLIENT_APP_NOTIFICATION_ID = 1001

    fun handle(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CLIENT_APP_QR_ACTION_BOOLEAN_EXTRA, true)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            CLIENT_APP_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationService = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel(
                    CLIENT_APP_NOTIFICATION_CHANNEL_ID,
                    CLIENT_APP_NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            notificationService.createNotificationChannel(notificationChannel)
        }

        val builder = NotificationCompat.Builder(context, CLIENT_APP_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pending)
            .setContentTitle("Open QR SCANNER")
            .setContentText("FuturaeDemo app account is requested to scan a QR code.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that fires when the user taps the notification.
            .setContentIntent(pendingIntent)
            .setChannelId(CLIENT_APP_NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(true)

        notificationService.notify(CLIENT_APP_NOTIFICATION_ID, builder.build())
    }
}