package com.futurae.futuraedemo

import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.common.FuturaeSDKStatus
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException


class CustomFirebaseService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + job)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        coroutineScope.launch {
            val sdkStatus = FuturaeSDK.sdkState().value.status
            if (sdkStatus is FuturaeSDKStatus.Uninitialized || sdkStatus is FuturaeSDKStatus.Corrupted) {
                // Make sure to initialize SDK if need be
            }
            val messageData = message.data
            FuturaeSDK.client.operationsApi.handlePushNotification(messageData)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        coroutineScope.launch {
            val sdkStatus = FuturaeSDK.sdkState().value.status
            if (sdkStatus is FuturaeSDKStatus.Uninitialized || sdkStatus is FuturaeSDKStatus.Corrupted) {
                // Persist token or flag for upload, once SDK is initialized
                return@launch
            }
            try {
                FuturaeSDK.client.accountApi.registerFirebasePushToken(token)
            } catch (e: Throwable) {
                // upload failed. Handle errors and retry
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel(CancellationException("Service destroyed"))
    }
}