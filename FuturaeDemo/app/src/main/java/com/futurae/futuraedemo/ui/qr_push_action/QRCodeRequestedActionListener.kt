package com.futurae.futuraedemo.ui.qr_push_action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.futurae.sdk.utils.FTNotificationUtils
import timber.log.Timber

abstract class QRCodeRequestedActionListener {

    private var localBroadcastManager: LocalBroadcastManager? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val error = intent?.getStringExtra(FTNotificationUtils.PARAM_ERROR)
            if (!TextUtils.isEmpty(error)) {
                Timber.e("Received Intent '" + intent?.action + "' with error: " + error)
                return
            }

            if (intent?.action != FTNotificationUtils.INTENT_QRCODE_SCAN_REQUESTED) {
                return
            }

            /**
             * optionally handle expiration, auto-remove push
             *
             * val time = Date().time / 1_000
             * val expirationTime = intent.getLongExtra(NotificationUtils.PARAM_TIMEOUT_TIMESTAMP_LONG, 0)
             * val timeDiffSeconds = expirationTime - time
             */

            onQRCodeScanRequested()
        }
    }

    private val qrCodeActionIntentFilter = IntentFilter().apply {
        addAction(FTNotificationUtils.INTENT_QRCODE_SCAN_REQUESTED)
    }

    fun startListening(context: Context) {
        localBroadcastManager = LocalBroadcastManager.getInstance(context)
        localBroadcastManager?.registerReceiver(broadcastReceiver, qrCodeActionIntentFilter)
    }

    fun stopListening() {
        localBroadcastManager?.unregisterReceiver(broadcastReceiver)
    }

    abstract fun onQRCodeScanRequested()
}