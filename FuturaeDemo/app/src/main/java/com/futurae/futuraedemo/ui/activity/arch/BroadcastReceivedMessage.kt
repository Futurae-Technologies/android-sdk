package com.futurae.futuraedemo.ui.activity.arch

import android.content.Intent
import com.futurae.futuraedemo.util.getParcelable
import com.futurae.sdk.public_api.session.model.ApproveSession
import com.futurae.sdk.utils.FTNotificationUtils
import timber.log.Timber

sealed class BroadcastReceivedMessage(open val action: String?) {

    companion object {
        fun from(intent: Intent): BroadcastReceivedMessage {
            val error = intent.getStringExtra(FTNotificationUtils.PARAM_ERROR)
            if (!error.isNullOrEmpty()) {
                Timber.e("Received Intent '" + intent.action + "' with error: " + error)
                Timber.e(FTNotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR)

                return Error(action = intent.action, error = error)
            }

            return when (intent.action) {
                FTNotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE -> {
                    Timber.d(FTNotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)
                    val userId = intent.getStringExtra(FTNotificationUtils.PARAM_USER_ID)!!
                    val deviceId = intent.getStringExtra(FTNotificationUtils.PARAM_DEVICE_ID)!!
                    AccountUnenroll(userId = userId, deviceId = deviceId)
                }

                FTNotificationUtils.INTENT_APPROVE_AUTH_MESSAGE -> {
                    intent
                        .getParcelable(
                            FTNotificationUtils.PARAM_APPROVE_SESSION,
                            ApproveSession::class.java
                        )
                        ?.let {
                            ApproveAuth(
                                encryptedExtras = FTNotificationUtils.getEncyptedExtras(intent),
                                userId = FTNotificationUtils.getUserId(intent),
                                approveSession = it
                            )
                        }
                        ?: Error(action = intent.action, error = "Unable to parse ApproveSession")
                }

                FTNotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR -> {
                    Timber.e(FTNotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR)
                    Error(action = intent.action, error = "Generic Error")
                }

                else -> {
                    Unknown
                }
            }
        }
    }

    data class Error(
        override val action: String?,
        val error: String
    ) : BroadcastReceivedMessage(action)

    data class AccountUnenroll(
        val userId: String,
        val deviceId: String
    ) : BroadcastReceivedMessage(action = FTNotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)

    data class ApproveAuth(
        val encryptedExtras: String?,
        val userId: String?,
        val approveSession: ApproveSession
    ) : BroadcastReceivedMessage(action = FTNotificationUtils.INTENT_APPROVE_AUTH_MESSAGE)

    object Unknown : BroadcastReceivedMessage(null)
}