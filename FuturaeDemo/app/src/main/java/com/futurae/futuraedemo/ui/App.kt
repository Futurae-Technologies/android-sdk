package com.futurae.futuraedemo.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.utils.NotificationUtils
import timber.log.Timber

class App : Application() {

    var sdkInitialized = false

    private var isForeground = false

    private val notificationsHelper: NotificationsHelper by lazy {
        NotificationsHelper(this)
    }

    private val intentFilter = IntentFilter().apply {
        addAction(NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)
        addAction(NotificationUtils.INTENT_APPROVE_AUTH_MESSAGE)
        addAction(NotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR)
        addAction(NotificationUtils.INTENT_APPROVE_CANCEL_MESSAGE)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val error = intent.getStringExtra(NotificationUtils.PARAM_ERROR)
            if (!TextUtils.isEmpty(error)) {
                Timber.e("Received Intent '" + intent.action + "' with error: " + error)
                return
            }
            if(isForeground) {
                //handled by Activities
                return;
            }
            when (intent.action) {
                NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE -> {
                    Timber.d(NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)
                }

                NotificationUtils.INTENT_APPROVE_AUTH_MESSAGE -> {
                    (intent.getParcelableExtra(NotificationUtils.PARAM_APPROVE_SESSION) as? ApproveSession)?.let { session ->
                        notificationsHelper.showNotification(
                            "Futurae Notification",
                            "Received an approve auth notification",
                            session
                        )
                    }
                }

                NotificationUtils.INTENT_APPROVE_CANCEL_MESSAGE -> {
                    //no-op
                }

                NotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR -> Timber.e(
                    NotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        notificationsHelper.createNotificationChannel()
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                isForeground = false
            }

            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                isForeground = true
            }
        })
    }


}