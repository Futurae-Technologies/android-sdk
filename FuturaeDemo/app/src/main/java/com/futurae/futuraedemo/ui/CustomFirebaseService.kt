package com.futurae.futuraedemo.ui

import android.os.Bundle
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.sdk.FuturaeCallback
import com.futurae.sdk.FuturaeSDK
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber


class CustomFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if(FuturaeSDK.isSDKInitialized) {
            val messageData = message.data

            // convert map -> bundle
            val data = Bundle()
            messageData.entries.forEach { (k, v) ->
                data.putString(k, v)
            }

            val ftrNotificationFactory = FuturaeSDK.getFTRNotificationFactory()
            val notification = ftrNotificationFactory.createNotification(data)
            notification?.handle()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        if(FuturaeSDK.isSDKInitialized) {
            FuturaeSdkWrapper.client.registerPushToken(token, object : FuturaeCallback {
                override fun success() {
                    Timber.d("Token upload success")
                }

                override fun failure(t: Throwable) {
                    Timber.e("Token upload failure", t)
                }
            })
        }
    }
}