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
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.showInputDialog
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.account.model.AccountQuery
import com.futurae.sdk.public_api.account.model.EnrollAccount
import com.futurae.sdk.public_api.account.model.EnrollmentParams
import com.futurae.sdk.public_api.account.model.URI
import com.futurae.sdk.public_api.auth.model.ApproveParameters
import com.futurae.sdk.public_api.auth.model.RejectParameters
import com.futurae.sdk.public_api.auth.model.SessionId
import com.futurae.sdk.public_api.exception.FTAccountNotFoundException
import com.futurae.sdk.public_api.exception.FTEncryptedStorageCorruptedException
import com.futurae.sdk.public_api.exception.FTException
import com.futurae.sdk.public_api.exception.FTInvalidArgumentException
import com.futurae.sdk.public_api.exception.FTKeystoreOperationException
import com.futurae.sdk.public_api.session.model.ApproveInfo
import com.futurae.sdk.public_api.session.model.ApproveSession
import com.futurae.sdk.public_api.session.model.ById
import com.futurae.sdk.public_api.session.model.SessionInfoQuery
import com.futurae.sdk.utils.FTNotificationUtils
import com.futurae.sdk.utils.FTUriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class FuturaeActivity : AppCompatActivity() {

    protected var pendingUri: String? = null

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
        addAction(FTNotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)
        addAction(FTNotificationUtils.INTENT_APPROVE_AUTH_MESSAGE)
        addAction(FTNotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR)
        addAction(FTNotificationUtils.INTENT_APPROVE_CANCEL_MESSAGE)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val error = intent.getStringExtra(FTNotificationUtils.PARAM_ERROR)
            if (!TextUtils.isEmpty(error)) {
                Timber.e("Received Intent '" + intent.action + "' with error: " + error)
                return
            }
            when (intent.action) {
                FTNotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE -> {
                    Timber.d(FTNotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE)
                    val userId: String = intent.getStringExtra(FTNotificationUtils.PARAM_USER_ID)!!
                    val deviceId: String =
                        intent.getStringExtra(FTNotificationUtils.PARAM_DEVICE_ID)!!
                    onUnenroll(userId, deviceId)
                }

                FTNotificationUtils.INTENT_APPROVE_AUTH_MESSAGE -> {
                    (intent.getParcelableExtra(FTNotificationUtils.PARAM_APPROVE_SESSION) as? ApproveSession)?.let { approveSession ->
                        val userId = FTNotificationUtils.getUserId(intent)
                        val encryptedExtras = FTNotificationUtils.getEncyptedExtras(intent)

                        if (encryptedExtras == null) {
                            onApproveAuth(approveSession, null)
                        } else {
                            if (userId == null) {
                                Timber.e("User id is required for encrypted extras")
                                return
                            }
                            val decryptedExtras = try {
                                FuturaeSDK.client.operationsApi.decryptPushNotificationExtraInfo(
                                    userId = userId,
                                    encryptedExtrasString = encryptedExtras
                                )
                            } catch (e: FTAccountNotFoundException) {
                                Timber.e("Account not found: ${e.message}")
                                null
                            } catch (e: FTEncryptedStorageCorruptedException) {
                                Timber.e("Encrypted storage corrupted. Please use account recovery operation: ${e.message}")
                                null
                            } catch (e: FTKeystoreOperationException) {
                                Timber.e("Keystore operation failed: ${e.message}")
                                null
                            } catch (e: FTInvalidArgumentException) {
                                Timber.e("Unable to parse decrypted extra info: ${e.message}")
                                null
                            }
                            onApproveAuth(approveSession, decryptedExtras)
                        }
                    }
                }

                FTNotificationUtils.INTENT_APPROVE_CANCEL_MESSAGE -> {
                    //no-op
                }

                FTNotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR -> Timber.e(
                    FTNotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR
                )
            }
        }
    }

    fun onApproveAuth(
        session: ApproveSession,
        decryptedExtras: List<ApproveInfo>?,
    ) {
        showDialog(
            "approve",
            "Would you like to approve the request?${session.toDialogMessage()} " +
                    "\n ${decryptedExtras?.let { "PN decrypted extras: ${it.joinToString()}" }}",
            "Approve",
            { approveAuth(session) },
            "Deny",
            { rejectAuth(session) })
    }

    /**
     * A method to allow the App the request unlock if necessary. Then resume if succesful via callback
     */
    fun onReceivedUri(callback: () -> Unit) {
        if (FuturaeSDK.client.lockApi.isLocked()) {
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
        if (FuturaeSDK.isAdaptiveEnabled()) {
            permissionLauncher.launch(
                requestAdaptivePermissions()
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        intent?.dataString?.takeIf { it.isNotBlank() }?.let {
            pendingUri = it
        }
    }

    fun handleUri(uriCall: String) {
        onReceivedUri {
            try {
                if (FTUriUtils.isEnrollUri(uriCall)) {
                    // Optional: you may use the enrollAccount API instead of handleUri API,
                    // to support flow-binding-token
                    showInputDialog("Flow Binding Token") {
                        lifecycleScope.launch {
                            FuturaeSDK.client.accountApi.enrollAccount(
                                EnrollmentParams(
                                    URI(uriCall),
                                    EnrollAccount,
                                    it
                                )
                            ).await()
                            showToast("URI Enrollment complete")
                        }
                    }
                } else {
                    lifecycleScope.launch {
                        FuturaeSDK.client.operationsApi.handleUri(uriCall).await()
                        showToast("URI handled")
                    }
                }
            } catch (t: FTException) {
                showErrorAlert("URI Error", t)
            }
        }
        pendingUri = null
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        //Handle URI from intent
        intent?.dataString?.takeIf { it.isNotBlank() }?.let { uriCall ->
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
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    protected fun onUnenroll(userId: String, deviceId: String) {
        FuturaeSDK.client.accountApi.getAccount(
            AccountQuery.WhereUserIdAndDevice(userId, deviceId)
        )?.let {
            FuturaeSDK.client.accountApi.logoutAccountOffline(userId)
        }
    }

    protected fun rejectAuth(session: ApproveSession) = lifecycleScope.launch {
        val userIdInSession = session.userId
        if (userIdInSession == null) {
            showAlertOnUIThread("API Error", "ApproveSession is incomplete")
            return@launch
        }

        val sessionInfoResult = try {
            FuturaeSDK.client.sessionApi.getSessionInfo(
                SessionInfoQuery(
                    ById(session.sessionId),
                    userIdInSession,
                )
            ).await()
        } catch (t: Throwable) {
            Timber.e(t)
            showAlertOnUIThread(
                "Session Api Error",
                "Error fetching session: \n" + t.message
            )
            return@launch
        }

        try {
            val rejectBuilder = RejectParameters.Builder(
                SessionId(userIdInSession, session.sessionId)
            )
            sessionInfoResult.approveInfo?.let {
                rejectBuilder.setExtraInfo(it)
            }

            FuturaeSDK.client.authApi.reject(
                rejectBuilder.build()
            ).await()
        } catch (t: Throwable) {
            Timber.e(t)
            showAlertOnUIThread("Auth Error", "Error rejecting session: " + t.message)
            return@launch
        }
        showToast("Rejected")
    }

    private fun showAlertOnUIThread(title: String, message: String) {
        showAlert(title, message)
    }

    protected fun approveAuth(session: ApproveSession) = lifecycleScope.launch {
        val userIdInSession = session.userId
        if (userIdInSession == null) {
            showAlertOnUIThread("API Error", "ApproveSession is incomplete")
            return@launch
        }

        val sessionInfoResult = try {
            FuturaeSDK.client.sessionApi.getSessionInfo(
                SessionInfoQuery(
                    ById(session.sessionId),
                    userIdInSession,
                )
            ).await()
        } catch (t: Throwable) {
            Timber.e(t)
            showAlertOnUIThread("API Error", "Error: \n" + t.message)
            return@launch
        }

        val userMultinumberChallengeInputOrNull =
            sessionInfoResult.multiNumberedChallenge?.let {
                getMultiEnrollResponseViaDialog(it.map { number -> number.toString() })
            }

        try {
            val transactionBuilder = ApproveParameters.Builder(
                SessionId(userIdInSession, session.sessionId)
            )
            userMultinumberChallengeInputOrNull?.let {
                transactionBuilder.setMultiNumberedChallengeChoice(it)
            }
            sessionInfoResult.approveInfo?.let {
                transactionBuilder.setExtraInfo(it)
            }
            FuturaeSDK.client.authApi.approve(
                transactionBuilder.build()
            ).await()
        } catch (t: FTException) {
            Timber.e(t)
            showAlertOnUIThread("Auth Error", "Error approving session: " + t.message)
            return@launch
        }

        showToast("Approved")
    }

    private suspend fun showToast(msg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(
            this@FuturaeActivity,
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }

    private suspend fun getMultiEnrollResponseViaDialog(
        items: List<String>
    ): Int = withContext(Dispatchers.Main) {
        return@withContext suspendCoroutine { continuation ->
            val alertDialog = AlertDialog.Builder(this@FuturaeActivity)
                .setItems(items.toTypedArray()) { _, itemIndex ->
                    val selectedItem = items.getOrNull(itemIndex)
                    if (selectedItem == null) {
                        continuation.resumeWithException(
                            IllegalStateException("UI Error: nonexistent item selected")
                        )
                        return@setItems
                    }
                    continuation.resume(
                        selectedItem.toInt()
                    )
                }
                .setCancelable(true)
                .setTitle("Select a number you see on the website")
                .setOnCancelListener {
                    continuation.resumeWithException(
                        IllegalArgumentException("Multi numbered challenge not provided")
                    )
                }
                .create()

            alertDialog.show()
        }
    }


    abstract fun showLoading()
    abstract fun hideLoading()
}