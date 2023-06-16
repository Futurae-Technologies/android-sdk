package com.futurae.futuraedemo.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.FuturaeCallback
import com.futurae.sdk.FuturaeClient
import com.futurae.sdk.FuturaeResultCallback
import com.futurae.sdk.adaptive.AdaptiveSDK
import com.futurae.sdk.adaptive.model.AdaptiveCollection
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.model.SessionInfo
import com.futurae.sdk.utils.NotificationUtils
import timber.log.Timber

abstract class FuturaeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI_STRING = "extra_uri_string"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResultMap ->
        if (permissionResultMap.values.contains(false)) {
            AlertDialog.Builder(this)
                .setTitle("Missing Adaptive Permissions")
                .setMessage("Make sure to grant all adaptive permissions to make the best out of the functionality. Press Settings to grant missing permissions")
                .setPositiveButton("Settings") { v, which ->
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
                .setNegativeButton("Cancel") { v, w ->
                    {
                        //nothing
                    }
                }
                .show()
        }
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
        if (FuturaeSdkWrapper.client.isLocked) {
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
        if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled) {
            permissionLauncher.launch(
                requestAdaptivePermissions()
            )
        }
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
                            if (sessionInfo == null) {
                                showDialog(
                                    "Something went wrong",
                                    "Please try again",
                                    "Ok",
                                    { }
                                )
                                return
                            }

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

    private fun requestAdaptivePermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.toTypedArray()
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
        var adaptiveCollection: AdaptiveCollection? = null
        if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled) {
            showLoading()
            AdaptiveSDK.INSTANCE.requestAdaptiveCollection(
                { dataUpdated -> adaptiveCollection = dataUpdated },
                { dataComplete -> adaptiveCollection = dataComplete },
                false
            )
        }
        FuturaeSdkWrapper.client
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
                                    if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled) {
                                        hideLoading()
                                        showAdaptiveAuthDialog(adaptiveCollection!!)
                                    } else {
                                        Toast.makeText(
                                            this@FuturaeActivity,
                                            "Rejected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                override fun failure(t: Throwable) {
                                    Timber.e(t)
                                    if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled) {
                                        hideLoading()
                                        showAdaptiveAuthDialog(adaptiveCollection!!, t)
                                    } else {
                                        showAlert("API Error", "Error: \n" + t.message)
                                    }
                                }
                            }, sessionInfo.approveInfo
                        )
                    }

                    override fun failure(t: Throwable) {
                        hideLoading()
                        showErrorAlert("API Error", t)
                    }
                })
    }

    protected fun approveAuth(session: ApproveSession) {
        var adaptiveCollection: AdaptiveCollection? = null
        if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled) {
            showLoading()
            AdaptiveSDK.INSTANCE.requestAdaptiveCollection(
                { dataUpdated -> adaptiveCollection = dataUpdated },
                { dataComplete -> adaptiveCollection = dataComplete },
                false
            )
        }
        FuturaeSdkWrapper.client
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
                                    if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled) {
                                        hideLoading()
                                        showAdaptiveAuthDialog(adaptiveCollection!!)
                                    } else {
                                        Toast.makeText(
                                            this@FuturaeActivity,
                                            "Approved",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                override fun failure(t: Throwable) {
                                    Timber.e(t)
                                    if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled) {
                                        hideLoading()
                                        showAdaptiveAuthDialog(adaptiveCollection!!, t)
                                    } else {
                                        showAlert("API Error", "Error: \n" + t.message)
                                    }
                                }
                            }, sessionInfo.approveInfo
                        )
                    }

                    override fun failure(t: Throwable) {
                        Timber.e(t)
                        hideLoading()
                        showAlert("API Error", "Error: \n" + t.message)
                    }
                })
    }

    abstract fun showLoading()
    abstract fun hideLoading()

    fun showAdaptiveAuthDialog(adaptiveCollection: AdaptiveCollection, error: Throwable? = null) {
        showDialog(
            if (error == null) "Auth success" else "Auth failed",
            if (error == null) "View your adaptive collection details?" else "Request failed with message:\n${error.message}\n View your adaptive collection details?",
            "View",
            {
                startActivity(AdaptiveCollectionDetailsActivity.newIntent(this, adaptiveCollection))
            }
        )
    }
}