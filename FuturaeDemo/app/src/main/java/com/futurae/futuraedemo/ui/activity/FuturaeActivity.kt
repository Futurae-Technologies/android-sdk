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
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.FuturaeCallback
import com.futurae.sdk.FuturaeResultCallback
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.adaptive.AdaptiveSDK
import com.futurae.sdk.adaptive.CompletionCallback
import com.futurae.sdk.adaptive.UpdateCallback
import com.futurae.sdk.adaptive.model.AdaptiveCollection
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.exception.LockOperationIsLockedException
import com.futurae.sdk.exception.LockUnexpectedException
import com.futurae.sdk.model.SessionInfo
import com.futurae.sdk.utils.FTUri
import com.futurae.sdk.utils.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class FuturaeActivity : AppCompatActivity() {

    protected var pendingUri : String? = null

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

                    val decryptedExtras = try {
                        FuturaeSdkWrapper.client.getDecryptedPushNotificationExtras(intent)
                    } catch (e: LockOperationIsLockedException) {
                        Timber.e("Please unlock SDK to get extras: ${e.message}")
                        null
                    } catch (e: IllegalStateException) {
                        Timber.e("Some data is missing for decryption: ${e.message}")
                        null
                    } catch (e: LockUnexpectedException) {
                        Timber.e("Cryptography error: ${e.message}")
                        null
                    }

                    (intent.getParcelableExtra(NotificationUtils.PARAM_APPROVE_SESSION) as? ApproveSession)?.let { session ->
                        onApproveAuth(session, hasExtraInfo, decryptedExtras)
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

    fun onApproveAuth(session: ApproveSession, hasExtraInfo: Boolean, decryptedExtras: JSONArray?) {
        showDialog(
            "approve",
            "Would you like to approve the request?${session.toDialogMessage()} \n extras: ${decryptedExtras?.toString()} \n ",
            "Approve",
            { approveAuth(session) },
            "Deny",
            { rejectAuth(session) })
    }

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
        if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) {
            permissionLauncher.launch(
                requestAdaptivePermissions()
            )
        }
        intent?.dataString?.takeIf { it.isNotBlank() }?.let {
            pendingUri = it
        }
    }

    fun handleUri(uriCall: String) {
        onReceivedUri {
            if (uriCall.contains("enroll")) {
                FuturaeSDK.getClient().handleUri(uriCall, object : FuturaeCallback {
                    override fun failure(throwable: Throwable?) {
                        showDialog("Error", "Could not handle URI call", "Ok", { })
                    }

                    override fun success() {
                        showDialog("Success", "Successfully enrolled", "Ok", { })
                    }
                })
            } else if (uriCall.contains("auth")) {

                val userId = FTUri.getUserIdFromUri(uriCall)
                val sessionToken = FTUri.getSessionTokenFromUri(uriCall)

                if(userId?.isNotBlank() == true && sessionToken?.isNotBlank() == true) {
                    FuturaeSDK.getClient().sessionInfoByToken(userId, sessionToken,
                        object : FuturaeResultCallback<SessionInfo> {
                            override fun success(result: SessionInfo?) {
                                if (result == null) {
                                    return
                                }
                                val session = ApproveSession(result)
                                if(session.hasExtraInfo()) {
                                    showDialog(
                                        "approve",
                                        "Would you like to approve the request?${session.toDialogMessage()}",
                                        "Approve",
                                        {
                                            FuturaeSDK.getClient().handleUri(uriCall, object : FuturaeCallback {
                                                override fun failure(throwable: Throwable?) {
                                                    showDialog("Error", "Could not handle URI call", "Ok", { })
                                                }

                                                override fun success() {
                                                    showDialog("Success", "Successfully approved", "Ok", { })
                                                }
                                            })
                                        },
                                        "Cancel",
                                        { })
                                } else {
                                    FuturaeSDK.getClient().handleUri(
                                        uriCall,
                                        object : FuturaeCallback {
                                            override fun success() {
                                                Toast.makeText(
                                                    this@FuturaeActivity,
                                                    "Approved",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }

                                            override fun failure(throwable: Throwable?) {
                                                Timber.e(throwable)
                                            }

                                        }
                                    )
                                }

                            }

                            override fun failure(throwable: Throwable?) {
                                Timber.e(throwable)
                            }
                        })
                } else {
                    showDialog("Error", "Uri did not contain userId and/or session token", "Ok", { })
                }
            } else {
                showDialog("Error", "Could not handle URI call", "Ok", { })
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
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    protected fun onUnenroll(userId: String, deviceId: String) {
        FuturaeSDK.getClient().getAccountByUserIdAndDeviceId(userId, deviceId)?.let {
            FuturaeSDK.getClient().deleteAccount(it.userId)
        }
    }

    protected fun rejectAuth(session: ApproveSession) {
        var adaptiveCollection: AdaptiveCollection? = null
        if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) {
            showLoading()
            // Calling this to register callback for the collection. For debugging purposes
            AdaptiveSDK.requestAdaptiveCollection(
                object : UpdateCallback {
                    override fun onCollectionDataUpdated(data: AdaptiveCollection) {
                        adaptiveCollection = data
                    }
                },
                object : CompletionCallback {
                    override fun onCollectionCompleted(data: AdaptiveCollection) {
                        adaptiveCollection = data
                    }
                },
                false
            )
        }
        FuturaeSdkWrapper.client
            .sessionInfoById(
                session.userId,
                session.sessionId,
                object : FuturaeResultCallback<SessionInfo> {
                    override fun success(result: SessionInfo?) {
                        val sessionInfo = result ?: return
                        sessionInfo.userId?.let {
                            FuturaeSDK.getClient().rejectAuth(
                                it,
                                sessionInfo.sessionId,
                                false,
                                object : FuturaeCallback {

                                    override fun success() {
                                        if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) {
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

                                    override fun failure(throwable: Throwable?) {
                                        val throwable = throwable ?: return
                                        Timber.e(throwable)
                                        if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) {
                                            hideLoading()
                                            showAdaptiveAuthDialog(adaptiveCollection!!, throwable)
                                        } else {
                                            showErrorAlert("API Error", throwable)
                                        }
                                    }
                                }, sessionInfo.approveInfo
                            )
                        } ?: showAlert("SDK Error", "Session info doesn't contain a userId.")
                    }

                    override fun failure(throwable: Throwable?) {
                        hideLoading()
                        throwable?.let { showErrorAlert("API Error", it ) }
                    }
                })
    }

    private fun showAlertOnUIThread(title: String, message: String) {
        showAlert(title, message)
    }

    protected fun approveAuth(session: ApproveSession) = lifecycleScope.launch {
        val userIdInSession = session.userId
        if (userIdInSession == null) {
            showAlertOnUIThread("API Error", "ApproveSession is incomplete" )
            return@launch
        }

        val sessionInfoResult = try {
            FuturaeSdkWrapper.coroutines.sessionInfoById(
                session.userId,
                session.sessionId
            ).getOrThrow()
        } catch (t: Throwable) {
            Timber.e(t)
            showAlertOnUIThread("API Error", "Error: \n" + t.message)
            return@launch
        }

        val userMultinumberChallengeInputOrNull = sessionInfoResult.multiNumberedChallenge?.let {
            getMultiEnrollResponseViaDialog(it.map { number -> number.toString() })
        }

        try {
            FuturaeSdkWrapper.coroutines.approveAuth(
                userIdInSession,
                session.sessionId,
                sessionInfoResult.approveInfo,
                userMultinumberChallengeInputOrNull
            )
        } catch (t: Throwable) {
            Timber.e(t)

            withContext(Dispatchers.Main) {
                showAlert("API Error", "Error: \n" + t.message)
            }

            return@launch
        }

        showApprovedToast()
    }

    private suspend fun showApprovedToast()
        = withContext(Dispatchers.Main)  {
        Toast.makeText(
            this@FuturaeActivity,
            "Approved",
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