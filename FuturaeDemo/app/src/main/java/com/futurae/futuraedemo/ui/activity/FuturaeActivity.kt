package com.futurae.futuraedemo.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.AccountSelectionSheet
import com.futurae.futuraedemo.ui.activeunlockmethodspicker.ActiveUnlockMethodPickerSheet
import com.futurae.futuraedemo.ui.activity.arch.DoOnUnlockMethodPicked
import com.futurae.futuraedemo.ui.activity.arch.FuturaeViewModel
import com.futurae.futuraedemo.ui.fragment.FragmentPin
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.showInputDialog
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.account.model.EnrollAccount
import com.futurae.sdk.public_api.account.model.EnrollmentParams
import com.futurae.sdk.public_api.account.model.URI
import com.futurae.sdk.public_api.auth.model.ApproveParameters
import com.futurae.sdk.public_api.auth.model.RejectParameters
import com.futurae.sdk.public_api.auth.model.SessionId
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForBiometricsPrompt
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForDeviceCredentialsPrompt
import com.futurae.sdk.public_api.exception.FTException
import com.futurae.sdk.public_api.lock.model.UnlockMethodType
import com.futurae.sdk.public_api.lock.model.WithBiometrics
import com.futurae.sdk.public_api.lock.model.WithBiometricsOrDeviceCredentials
import com.futurae.sdk.public_api.lock.model.WithSDKPin
import com.futurae.sdk.public_api.session.model.ApproveSession
import com.futurae.sdk.public_api.session.model.ById
import com.futurae.sdk.public_api.session.model.SessionInfoQuery
import com.futurae.sdk.utils.FTUriUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class FuturaeActivity : AppCompatActivity() {

    private val viewModel: FuturaeViewModel by viewModels()

    protected var pendingUri: String? = null

    protected val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResultMap ->
        if (permissionResultMap.values.contains(false)) {
            AlertDialog.Builder(this)
                .setTitle("Missing Adaptive Permissions")
                .setMessage("Make sure to grant all adaptive permissions to make the best out of the functionality. Press Settings to grant missing permissions")
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
                .setNegativeButton("Cancel") { _, _ ->
                    //nothing
                }
                .show()
        }
    }

    fun showApproveAuthConfirmationDialog(
        session: ApproveSession,
        additionalMessage: String?,
    ) {
        showDialog(
            "approve",
            "Would you like to approve the request?${session.toDialogMessage()} " +
                    "\n $additionalMessage",
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        intent?.dataString?.takeIf { it.isNotBlank() }?.let {
            pendingUri = it
        }

        setUpObservers()
    }

    fun handleUri(uriCall: String) {
        onReceivedUri {
            val exceptionHandler = CoroutineExceptionHandler { _, t ->
                showErrorAlert("URI Error", t)
            }

            try {
                if (FTUriUtils.isEnrollUri(uriCall)) {
                    // Optional: you may use the enrollAccount API instead of handleUri API,
                    // to support flow-binding-token
                    showInputDialog("Flow Binding Token") {
                        lifecycleScope.launch(exceptionHandler) {
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
                    lifecycleScope.launch(exceptionHandler) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        //Handle URI from intent
        intent.dataString?.takeIf { it.isNotBlank() }?.let { uriCall ->
            handleUri(uriCall)
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

    private fun approveAuth(session: ApproveSession) = lifecycleScope.launch {
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

    private fun setUpObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notifyUser.collect { (title, message) ->
                        showAlertOnUIThread(title = title, message = message)
                    }
                }

                launch {
                    viewModel.notifyUserAboutError.collect { (title, throwable) ->
                        showErrorAlert(title = title, throwable = throwable)
                    }
                }

                launch {
                    viewModel.promptUserToPickUnlockMethod.collect {
                        promptUserToPickUnlockMethod(it)
                    }
                }

                launch {
                    viewModel.promptUserForApproval.collect { (approveSession, approveInfo) ->
                        showApproveAuthConfirmationDialog(approveSession, approveInfo)
                    }
                }

                launch {
                    viewModel.onUnlockMethodSelected.collect { (unlockMethodType, doOnSdkUnlocked) ->
                        unlockSdk(unlockMethodType) {
                            doOnSdkUnlocked()
                        }
                    }
                }
            }
        }
    }

    private fun unlockSdk(
        unlockMethodType: UnlockMethodType,
        onSDKUnlocked: () -> Unit
    ) {
        when (unlockMethodType) {
            UnlockMethodType.BIOMETRICS -> {
                val userPresenceVerificationMode = WithBiometrics(
                    PresentationConfigurationForBiometricsPrompt(
                        this,
                        "Unlock SDK",
                        "Authenticate with biometrics",
                        "Authentication is required to unlock SDK operations",
                        "cancel",
                    )
                )
                viewModel.unlockSdk(userPresenceVerificationMode, onSDKUnlocked)
            }
            UnlockMethodType.BIOMETRICS_OR_DEVICE_CREDENTIALS -> {
                val userPresenceVerificationMode = WithBiometricsOrDeviceCredentials(
                    PresentationConfigurationForDeviceCredentialsPrompt(
                        this,
                        "Unlock SDK",
                        "Authenticate with biometrics",
                        "Authentication is required to unlock SDK operations",
                    )
                )
                viewModel.unlockSdk(userPresenceVerificationMode, onSDKUnlocked)
            }
            UnlockMethodType.SDK_PIN -> {
                supportFragmentManager.getPinWithCallback {
                    val userPresenceVerificationMode = WithSDKPin(it)
                    viewModel.unlockSdk(userPresenceVerificationMode, onSDKUnlocked)
                }
            }
        }
    }

    private fun FragmentManager.getPinWithCallback(callback: (CharArray) -> Unit) {
        val pinFragment = FragmentPin()

        beginTransaction()
            .add(
                R.id.pinFragmentContainer,
                pinFragment.apply {
                    listener = object : FragmentPin.Listener {
                        override fun onPinComplete(pin: CharArray) {
                            parentFragmentManager.beginTransaction().remove(pinFragment).commit()
                            callback(pin)
                        }
                    }
                }
            )
            .commit()
    }

    private fun promptUserToPickUnlockMethod(onMethodPicked: DoOnUnlockMethodPicked) {
        val modalBottomSheet = ActiveUnlockMethodPickerSheet().apply {
            setOnActiveUnlockMethodPickedListener(object : ActiveUnlockMethodPickerSheet.Listener {
                override fun onUnlockMethodSelected(unlockMethod: UnlockMethodType) {
                    onMethodPicked(unlockMethod)
                }
            })
        }
        modalBottomSheet.show(supportFragmentManager, AccountSelectionSheet.TAG)
    }
}