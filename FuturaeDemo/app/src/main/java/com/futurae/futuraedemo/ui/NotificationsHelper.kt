package com.futurae.futuraedemo.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.activity.MainActivity
import com.futurae.sdk.approve.ApproveSession

const val CHANNEL_ID = "futurae_notifications"
const val NOTIFICATION_ID = 10000
const val EXTRA_APPROVE_SESSION = "EXTRA_APPROVE_SESSION"

class NotificationsHelper(private val context: Context) {

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

    }

    fun showNotification(title: String, body: String, session: ApproveSession) {
        // Create an explicit intent for an Activity in your app
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_APPROVE_SESSION, session)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, session.sessionId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

}