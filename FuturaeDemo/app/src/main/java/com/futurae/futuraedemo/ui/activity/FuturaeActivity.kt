package com.futurae.futuraedemo.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.futurae.futuraedemo.ui.showAlert
import com.futurae.futuraedemo.ui.showDialog
import com.futurae.futuraedemo.ui.toDialogMessage
import com.futurae.sdk.FuturaeCallback
import com.futurae.sdk.FuturaeClient
import com.futurae.sdk.FuturaeResultCallback
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.model.SessionInfo
import com.futurae.sdk.utils.NotificationUtils
import timber.log.Timber

abstract class FuturaeActivity : FragmentActivity() {

    companion object {
        const val EXTRA_URI_STRING = "extra_uri_string"
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
            when (intent.action) {
                NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE -> {
                    Timber.d(NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)
                    val userId: String = intent.getStringExtra(NotificationUtils.PARAM_USER_ID)!!
                    val deviceId: String =
                        intent.getStringExtra(NotificationUtils.PARAM_DEVICE_ID)!!
                    onUnenroll(userId, deviceId)
                }
                NotificationUtils.INTENT_APPROVE_AUTH_MESSAGE -> {
                    val hasExtraInfo =
                        intent.getBooleanExtra(NotificationUtils.PARAM_HAS_EXTRA_INFO, false)
                    (intent.getParcelableExtra(NotificationUtils.PARAM_APPROVE_SESSION) as? ApproveSession)?.let { session ->
                        onApproveAuth(session, hasExtraInfo)
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

    abstract fun onApproveAuth(session: ApproveSession, hasExtraInfo: Boolean)

    /**
     * A method to allow the App the request unlock if necessary. Then resume if succesful via callback
     */
    fun onReceivedUri(callback: () -> Unit) {
        if (FuturaeSDK.INSTANCE.getClient().isLocked) {
            showDialog(
                "URI received",
                "Received URI but SDK is locked. Please unlock to continue",
                "ok",
                { }
            )
        } else {
            callback()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra(EXTRA_URI_STRING)?.let {
            handleUri(it)
        }
    }

    fun handleUri(uriCall: String) {
        onReceivedUri {
            if (uriCall.contains("enroll")) {
                FuturaeClient.sharedClient().handleUri(uriCall, object : FuturaeCallback {
                    override fun success() {
                        showDialog("Success", "Successfully enrolled", "Ok", { })
                    }

                    override fun failure(throwable: Throwable) {
                        showDialog("Error", "Could not handle URI call", "Ok", { })
                    }
                })
            } else if (uriCall.contains("auth")) {

                val userId = FuturaeClient.getUserIdFromUri(uriCall)
                val sessionToken = FuturaeClient.getSessionTokenFromUri(uriCall)

                FuturaeClient.sharedClient().sessionInfoByToken(userId, sessionToken,
                    object : FuturaeResultCallback<SessionInfo?> {
                        override fun success(sessionInfo: SessionInfo?) {
                            val session = ApproveSession(sessionInfo)
                            showDialog(
                                "approve",
                                "Would you like to approve the request?${session.toDialogMessage()}",
                                "Approve",
                                { approveAuth(session) },
                                "Deny",
                                { rejectAuth(session) })
                        }

                        override fun failure(t: Throwable) {
                            Timber.e(t)
                        }
                    })
            } else {
                showDialog("Error", "Could not handle URI call", "Ok", { })
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        //Handle URI from intent
        intent?.getStringExtra(EXTRA_URI_STRING)?.takeIf { it.isNotBlank() }?.let { uriCall ->
            handleUri(uriCall)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    protected fun onUnenroll(userId: String, deviceId: String) {
        FuturaeClient.sharedClient().getAccountByUserIdAndDeviceId(userId, deviceId)?.let {
            FuturaeClient.sharedClient().deleteAccount(it.userId)
        }
    }

    protected fun rejectAuth(session: ApproveSession) {
        FuturaeSDK.INSTANCE.client
            .sessionInfoById(
                session.userId,
                session.sessionId,
                object : FuturaeResultCallback<SessionInfo> {
                    override fun success(sessionInfo: SessionInfo) {
                        FuturaeClient.sharedClient().rejectAuth(
                            sessionInfo.userId,
                            sessionInfo.sessionId,
                            false,
                            object : FuturaeCallback {
                                override fun success() {
                                    Toast.makeText(
                                        this@FuturaeActivity,
                                        "Rejected",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                override fun failure(t: Throwable) {
                                    showAlert("API Error", "Error: \n" + t.message)
                                }
                            }, sessionInfo.approveInfo
                        )
                    }

                    override fun failure(t: Throwable) {
                        showAlert("API Error", "Error: \n" + t.message)
                    }
                })
    }

    protected fun approveAuth(session: ApproveSession) {
        FuturaeSDK.INSTANCE.client
            .sessionInfoById(
                session.userId,
                session.sessionId,
                object : FuturaeResultCallback<SessionInfo> {
                    override fun success(sessionInfo: SessionInfo) {
                        FuturaeClient.sharedClient().approveAuth(
                            sessionInfo.userId,
                            sessionInfo.sessionId,
                            object : FuturaeCallback {
                                override fun success() {
                                    Toast.makeText(
                                        this@FuturaeActivity,
                                        "Approved",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                override fun failure(t: Throwable) {
                                    showAlert("API Error", "Error: \n" + t.message)
                                }
                            }, sessionInfo.approveInfo
                        )
                    }

                    override fun failure(t: Throwable) {
                        showAlert("API Error", "Error: \n" + t.message)
                    }
                })
    }
}