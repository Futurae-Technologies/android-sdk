package com.futurae.futuraedemo.ui.fragment

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.AccountSelectionSheet
import com.futurae.futuraedemo.ui.activity.ActivityAccountHistory
import com.futurae.futuraedemo.ui.activity.FTRQRCodeActivity
import com.futurae.futuraedemo.ui.activity.FuturaeActivity
import com.futurae.futuraedemo.ui.qr_push_action.QRCodeFlowOpenCoordinator
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.futuraedemo.util.getParcelable
import com.futurae.futuraedemo.util.getSessionInfo
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.showInputDialog
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.account.model.AccountsStatus
import com.futurae.sdk.public_api.account.model.ActivationCode
import com.futurae.sdk.public_api.account.model.EnrollAccount
import com.futurae.sdk.public_api.account.model.EnrollAccountAndSetupSDKPin
import com.futurae.sdk.public_api.account.model.EnrollmentParams
import com.futurae.sdk.public_api.account.model.ShortActivationCode
import com.futurae.sdk.public_api.auth.model.ApproveParameters
import com.futurae.sdk.public_api.auth.model.OnlineQR
import com.futurae.sdk.public_api.auth.model.RejectParameters
import com.futurae.sdk.public_api.auth.model.SDKAuthMode
import com.futurae.sdk.public_api.auth.model.UsernamelessQR
import com.futurae.sdk.public_api.common.FuturaeSDKStatus
import com.futurae.sdk.public_api.common.LockConfigurationType
import com.futurae.sdk.public_api.common.model.FTAccount
import com.futurae.sdk.public_api.exception.FTMalformedQRCodeException
import com.futurae.sdk.public_api.exception.FTUnlockRequiredException
import com.futurae.sdk.public_api.migration.model.MigrationAccount
import com.futurae.sdk.public_api.migration.model.MigrationUseCase
import com.futurae.sdk.public_api.session.model.ApproveInfo
import com.futurae.sdk.public_api.session.model.ApproveSession
import com.futurae.sdk.public_api.session.model.ByToken
import com.futurae.sdk.public_api.session.model.SessionInfoQuery
import com.futurae.sdk.utils.FTQRCodeUtils
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A fragment that performs common Futurae SDK operations. The lock fragments can inherit this so they
 * can implement only the lock/unlocking flow.
 */
abstract class FragmentSDKOperations : BaseFragment() {
    abstract fun serviceLogoButton(): MaterialButton
    abstract fun timeLeftView(): TextView
    abstract fun sdkStatus(): TextView
    abstract fun accountInfoButton(): View


    protected val localStorage: LocalStorage by lazy {
        LocalStorage(requireContext())
    }
    private val getQRCodeCallback =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    onQRCodeScanned(intent)
                } ?: throw IllegalStateException("Activity result without Intent")
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serviceLogoButton().setOnClickListener {
            val ftAccounts = getAccounts()
            if (ftAccounts.isNotEmpty()) {
                val ftAccount = ftAccounts.first()
                val dialogView = layoutInflater.inflate(R.layout.dialog_service_logo, null)
                val imageView = dialogView.findViewById<ImageView>(R.id.logoImage)
                Glide.with(this).load(ftAccount.serviceLogo).placeholder(R.drawable.ic_content_copy)
                    .into(imageView)
                val dialog =
                    AlertDialog.Builder(
                        requireContext(),
                        com.google.android.material.R.style.Theme_Material3_Light_Dialog
                    )
                        .setTitle("Service Logo")
                        .setView(dialogView).setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }.create()
                dialog.show()
            } else {
                Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
            }
        }

        accountInfoButton().setOnClickListener {
            val ftAccounts = getAccounts()
            if (ftAccounts.isNotEmpty()) {
                val ftAccount = ftAccounts.first()
                showAlert(
                    "Account Info",
                    ("Account id: ${ftAccount.userId}," +
                            "\nAccount username: ${ftAccount.username}," +
                            "\nService name: ${ftAccount.serviceName}").trimIndent()
                )
            } else {
                Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
            }
        }

        accountInfoButton().setOnClickListener {
            val ftAccounts = getAccounts()
            if (ftAccounts.isNotEmpty()) {
                val ftAccount = ftAccounts.first()
                showAlert(
                    "Account Info",
                    ("Account id: ${ftAccount.userId}," +
                            "\nAccount username: ${ftAccount.username}," +
                            "\nService name: ${ftAccount.serviceName}").trimIndent()
                )
            } else {
                Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            FuturaeSDK.sdkState().collect {
                when (it.status) {
                    is FuturaeSDKStatus.Corrupted -> sdkStatus().text = "SDK Corrupted"
                    FuturaeSDKStatus.Locked -> sdkStatus().text = "Locked"
                    FuturaeSDKStatus.Uninitialized -> sdkStatus().text = "Not initialized"
                    is FuturaeSDKStatus.Unlocked -> sdkStatus().text = "Unlocked"
                }
                if (it.isBlocked) {
                    sdkStatus().text = "Blocked by operation"
                }
                timeLeftView().text = "${it.remainingUnlockedTime}"
            }
        }
    }

    protected fun getAccountHistory() {
        getAccounts().firstOrNull()?.let {
            startActivity(Intent(requireContext(), ActivityAccountHistory::class.java))
        } ?: Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
    }

    protected fun getAccountsStatus() {
        getAccounts().takeIf { it.isNotEmpty() }
            ?.let { accounts ->
                lifecycleScope.launch {
                    try {
                        val accountsStatus =
                            FuturaeSDK.client.accountApi.getAccountsStatus(
                                *accounts.map { it.userId }.toTypedArray()
                            ).await()
                        val sessionInfos = accountsStatus.statuses.first().activeSessions
                        if (sessionInfos.isNotEmpty()) {
                            val session = ApproveSession(sessionInfos.first())
                            (activity as FuturaeActivity).showApproveAuthConfirmationDialog(
                                session,
                                null
                            )
                        } else {
                            showAccountStatusInfo(accountsStatus)
                        }
                    } catch (t: Throwable) {
                        showErrorAlert("Account Status Error", t)
                    }
                }
            } ?: Toast.makeText(requireContext(), "No accounts enrolled", Toast.LENGTH_SHORT).show()
    }

    private fun showAccountStatusInfo(result: AccountsStatus) {
        val serviceIdsEnrolled: List<String> = getAccounts().map { it.serviceId }
        val featureFlagsText = result.featureFlags.joinToString { ff ->
            val defaultEnabled = ff.isEnabled
            val exceptionParam =
                ff.params.firstOrNull { param -> param.serviceIds.any { it in serviceIdsEnrolled } }
            val updatedValue = exceptionParam?.isEnabled ?: defaultEnabled
            "\n ${ff.name}:$updatedValue"
        }
        showAlert(
            "Accounts Status Response",
            "Statuses: \n ${result.statuses.joinToString { accStat -> accStat.userId + "\n" }}"
                    + "Feature Flags: $featureFlagsText"
        )
    }

    protected fun scanQRCode() {
        getQRCodeCallback.launch(
            FTRQRCodeActivity.getIntent(requireContext(), true, false),
        )
    }

    protected fun onTOTPAuth() {
        val accounts = getAccounts()
        if (accounts.isEmpty()) {
            showAlert("Error", "No account enrolled")
            return
        }
        lifecycleScope.launch {
            val account = accounts[0]
            try {
                val totp = FuturaeSDK.client.authApi.getTOTP(
                    account.userId,
                    SDKAuthMode.Unlock
                ).await()

                showAlert(
                    "TOTP", "Code: ${totp.passcode}\nRemaining seconds: ${totp.remainingSeconds}"
                )
            } catch (e: FTUnlockRequiredException) {
                Timber.e(e)
                showErrorAlert("SDK Unlock", e)
            }
        }
    }

    protected fun onHotpAuth() {
        val accounts = getAccounts()
        if (accounts.isEmpty()) {
            showAlert("Error", "No account enrolled")
            return
        }
        val account = accounts[0]
        try {
            val hotp = FuturaeSDK.client.authApi.getNextSynchronousAuthToken(account.userId)
            showDialog("TOTP", "HOTP JWT: ${hotp}\n", "OK", {
                Timber.e("JWT: ${hotp}")
                val clipboardMgr = getSystemService(requireContext(), ClipboardManager::class.java)
                val clip = ClipData.newPlainText("HOTP JWT", hotp)
                clipboardMgr?.setPrimaryClip(clip)
            })
        } catch (e: FTUnlockRequiredException) {
            Timber.e(e)
            showErrorAlert("SDK Unlock", e)
        }
    }

    protected fun attemptRestoreAccounts() {
        lifecycleScope.launch {
            try {
                val result = FuturaeSDK.client.migrationApi.getMigratableAccounts().await()
                if (result.migratableAccountInfos.isNotEmpty()) {
                    val requiresPinProtection =
                        result.pinProtected || localStorage.getPersistedSDKConfig().lockConfigurationType == LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL
                    showDialog(
                        "SDK Account Migration",
                        "Restoring Accounts possible for:\n ${result.migratableAccountInfos.size} accounts."
                                + "\n Requires PIN: $requiresPinProtection"
                                + "\n Requires Adaptive: ${result.adaptiveEnabled}"
                                + "\n Account names: \n ${result.migratableAccountInfos.joinToString { acc -> acc.username + ",\n" }}"
                                + "\n Device IDs: \n ${result.migratableAccountInfos.joinToString { acc -> acc.deviceId + ",\n" }}",
                        "Proceed",
                        {
                            if (requiresPinProtection) {
                                onAccountsMigrationWithSDKPINExecute()
                            } else {
                                onAccountsMigrationExecute()
                            }
                        },
                        "cancel",
                    )
                } else {
                    showAlert(
                        "SDK Account Migration",
                        "No migratable accounts found"
                    )
                }
            } catch (t: Throwable) {
                showErrorAlert("SDK Unlock", t)
            }

        }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            QRCodeFlowOpenCoordinator.instance.flow.collect { shouldOpenQrScanner ->
                if (!shouldOpenQrScanner) {
                    return@collect
                }

                QRCodeFlowOpenCoordinator.instance.notifyShouldOpenQRCodeConsumed()

                scanQRCode()
            }
        }
    }

    private fun onAccountsMigrationExecute() {
        requireContext().showInputDialog("Flow Binding Token") { token ->
            lifecycleScope.launch {
                try {
                    val result = FuturaeSDK.client.migrationApi.migrateAccounts(
                        MigrationUseCase.AccountsNotSecuredWithPinCode,
                        token
                    ).await()
                    onMigrationSuccess(result)
                } catch (t: Throwable) {
                    showErrorAlert("SDK Unlock", t)
                }
            }
        }
    }

    private fun onMigrationSuccess(migrationAccounts: List<MigrationAccount>) {
        val userIdsMigrated = StringBuilder()
        for (i in migrationAccounts.indices) {
            if (i > 0) {
                userIdsMigrated.append("\n")
            }
            userIdsMigrated.append(migrationAccounts[i].username)
        }
        Timber.i("Accounts migration successful. Accounts migrated (total: " + migrationAccounts.size + "): " + userIdsMigrated)
        showAlert(
            "Accounts migration successful",
            """Accounts migrated (total: ${migrationAccounts.size}):$userIdsMigrated""".trimIndent()
        )
    }

    private fun onAccountsMigrationWithSDKPINExecute() {
        getPinWithCallback { pin ->
            requireContext().showInputDialog("Flow Binding Token") { token ->
                lifecycleScope.launch {
                    try {
                        val result = FuturaeSDK.client.migrationApi.migrateAccounts(
                            MigrationUseCase.AccountsSecuredWithPinCode(pin),
                            token,
                        ).await()
                        onMigrationSuccess(result)
                    } catch (t: Throwable) {
                        showErrorAlert("SDK Unlock", t)
                    }
                }
            }
        }
    }

    // QRCode callbacks
    private fun onEnrollQRCodeScanned(data: Intent) {
        data.getParcelable(
            FTRQRCodeActivity.PARAM_BARCODE,
            Barcode::class.java
        )?.let { qrcode ->
            requireContext().showInputDialog("Flow Binding Token") { token ->
                lifecycleScope.launch {
                    try {
                        FuturaeSDK.client.accountApi.enrollAccount(
                            EnrollmentParams(
                                inputCode = ActivationCode(qrcode.rawValue),
                                enrollmentUseCase = EnrollAccount,
                                flowBindingToken = token
                            )
                        ).await()
                        Toast.makeText(requireContext(), "Account Enrolled", Toast.LENGTH_SHORT)
                            .show()
                    } catch (t: Throwable) {
                        Timber.e(t)
                        showErrorAlert("Enroll API error", t)
                    }
                }
            }
        }
    }

    protected fun onManualEntryEnroll(sdkPin: CharArray? = null) {
        requireContext().showInputDialog("Activation Shortcode") { code ->
            val enrollUseCase = if (sdkPin != null) {
                EnrollAccountAndSetupSDKPin(
                    sdkPin = sdkPin
                )
            } else {
                EnrollAccount
            }

            requireContext().showInputDialog("Flow Binding Token") { token ->
                lifecycleScope.launch {
                    try {
                        FuturaeSDK.client.accountApi.enrollAccount(
                            enrollmentParameters = EnrollmentParams(
                                ShortActivationCode(code),
                                enrollUseCase,
                                token
                            )
                        ).await()
                        showToast("Enrolled")
                    } catch (t: Throwable) {
                        showErrorAlert("Enroll Error", t)
                    }
                }
            }
        }
    }

    protected fun onActivationCodeEnroll(sdkPin: CharArray? = null) {
        requireContext().showInputDialog("Activation code") { code ->
            val enrollUseCase = if (sdkPin != null) {
                EnrollAccountAndSetupSDKPin(
                    sdkPin = sdkPin
                )
            } else {
                EnrollAccount
            }

            requireContext().showInputDialog("Flow Binding Token") { token ->
                lifecycleScope.launch {
                    try {
                        FuturaeSDK.client.accountApi.enrollAccount(
                            enrollmentParameters = EnrollmentParams(
                                ActivationCode(code),
                                enrollUseCase,
                                token
                            )
                        ).await()
                        showToast("Enrolled")
                    } catch (t: Throwable) {
                        showErrorAlert("Enroll Error", t)
                    }
                }
            }
        }
    }

    private fun onAuthQRCodeScanned(data: Intent) {
        data.getParcelable(
            FTRQRCodeActivity.PARAM_BARCODE,
            Barcode::class.java
        )?.let { qrcode ->
            val userId: String?
            val sessionToken: String?
            try {
                userId = FTQRCodeUtils.getUserIdFromQrcode(qrcode.rawValue)
                sessionToken = FTQRCodeUtils.getSessionTokenFromQrcode(qrcode.rawValue)
            } catch (e: FTMalformedQRCodeException) {
                showErrorAlert("QR Error", e)
                return
            }

            lifecycleScope.launch {
                try {
                    val sessionInfo = getSessionInfo(
                        query = SessionInfoQuery(
                            ByToken(sessionToken),
                            userId,
                        ),
                        isPhysicalDeviceSessionInfoEnabled = localStorage.isUnprotectedSessionInfoEnabled
                    )

                    ApproveSession(sessionInfo)
                        .takeIf { it.userId?.isNotBlank() == true }
                        ?.let { session ->
                            showDialog(
                                "approve",
                                "Would you like to approve the request?${session.toDialogMessage()}",
                                "Approve",
                                {
                                    lifecycleScope.launch {
                                        try {
                                            val paramBuilder = ApproveParameters.Builder(
                                                OnlineQR(qrcode.rawValue)
                                            )
                                            sessionInfo.approveInfo?.let {
                                                paramBuilder.setExtraInfo(it)
                                            }
                                            FuturaeSDK.client.authApi.approve(
                                                paramBuilder.build()
                                            ).await()
                                            showToast("Approved")
                                        } catch (t: Throwable) {
                                            showErrorAlert("Auth API Error", t)
                                        }

                                    }
                                },
                                "Deny",
                                {
                                    lifecycleScope.launch {
                                        try {
                                            val paramBuilder = RejectParameters.Builder(
                                                OnlineQR(qrcode.rawValue)
                                            )
                                            sessionInfo.approveInfo?.let {
                                                paramBuilder.setExtraInfo(it)
                                            }

                                            FuturaeSDK.client.authApi.reject(
                                                paramBuilder.build()
                                            ).await()
                                            showToast("Rejected")
                                        } catch (t: Throwable) {
                                            showErrorAlert("Auth API Error", t)
                                        }
                                    }
                                })
                        }
                        ?: showErrorAlert(
                            "SDK Error",
                            Throwable("Session is missing user id")
                        )
                } catch (t: Throwable) {
                    showErrorAlert("Session API error", t)
                }
            }
        }
    }

    protected fun onAnonymousQRCodeScanned(data: Intent) {
        data.getParcelable(FTRQRCodeActivity.PARAM_BARCODE, Barcode::class.java)
            ?.let { barcode ->
                val modalBottomSheet = AccountSelectionSheet().apply {
                    listener = object : AccountSelectionSheet.Listener {
                        override fun onAccountSelected(userId: String) {
                            handleUsernamelessQr(barcode.rawValue, userId)
                        }
                    }
                }
                modalBottomSheet.show(childFragmentManager, AccountSelectionSheet.TAG)
            }
    }

    private fun handleUsernamelessQr(qrCode: String, userId: String) {
        val sessionToken = FTQRCodeUtils.getSessionTokenFromQrcode(qrCode)
        lifecycleScope.launch {
            val sessionInfo = try {
                getSessionInfo(
                    query = SessionInfoQuery(
                        ByToken(sessionToken),
                        userId,
                    ),
                    isPhysicalDeviceSessionInfoEnabled = localStorage.isUnprotectedSessionInfoEnabled
                )
            } catch (t: Throwable) {
                showErrorAlert("Session API error", t)
                return@launch
            }
            val approveSession = ApproveSession(sessionInfo)
            showDialog("approve",
                "Would you like to approve the request?${approveSession.toDialogMessage()}",
                "Approve",
                {
                    lifecycleScope.launch {
                        val paramBuilder = ApproveParameters.Builder(
                            UsernamelessQR(userId, qrCode)
                        )
                        sessionInfo.approveInfo?.takeIf { it.isNotEmpty() }?.let {
                            paramBuilder.setExtraInfo(it)
                        }
                        try {
                            FuturaeSDK.client.authApi.approve(paramBuilder.build()).await()
                            showToast("Approved")
                        } catch (t: Throwable) {
                            showErrorAlert("Auth API Error", t)
                        }
                    }
                },
                "Reject",
                {
                    lifecycleScope.launch {
                        val paramBuilder = RejectParameters.Builder(
                            UsernamelessQR(userId, qrCode)
                        )
                        sessionInfo.approveInfo?.takeIf { it.isNotEmpty() }?.let {
                            paramBuilder.setExtraInfo(it)
                        }
                        try {
                            FuturaeSDK.client.authApi.reject(paramBuilder.build()).await()
                            showToast("Rejected")
                        } catch (t: Throwable) {
                            showErrorAlert("Auth API Error", t)
                        }
                    }
                })
        }
    }

    protected fun onOfflineAuthQRCodeScanned(data: Intent) {
        data.getParcelable(
            FTRQRCodeActivity.PARAM_BARCODE,
            Barcode::class.java
        )?.let { barcode ->
            val qrCode = barcode.rawValue
            val extras: List<ApproveInfo>? = try {
                FuturaeSDK.client.sessionApi.extractQRCodeExtraInfo(qrCode)
            } catch (e: FTMalformedQRCodeException) {
                Timber.e(e)
                showErrorAlert("SDK Unlock", e)
                return
            }
            val sb = StringBuffer()
            if (extras != null) {
                sb.append("\n")
                for (info in extras) {
                    sb.append(info.key).append(": ").append(info.value).append("\n")
                }
            }
            showDialog("Approve", "Request Info: ${sb}", "Approve", {
                lifecycleScope.launch {
                    val verificationSignature = try {
                        FuturaeSDK.client.authApi.getOfflineQRVerificationCode(
                            qrCode,
                            SDKAuthMode.Unlock
                        ).await()
                    } catch (t: Throwable) {
                        showErrorAlert("SDK Unlock", t)
                        return@launch
                    }
                    showAlert(
                        "Confirm Transaction",
                        "To Approve the transaction, enter: $verificationSignature in the browser"
                    )
                }
            }, " Deny", {
                //Nothing
            })
        }
    }

    protected fun onQRCodeScanned(data: Intent) {
        data.getParcelable(
            FTRQRCodeActivity.PARAM_BARCODE,
            Barcode::class.java
        )?.let { qrcode ->
            when (FTQRCodeUtils.getQrcodeType(qrcode.rawValue)) {
                FTQRCodeUtils.QRType.Enroll -> onEnrollQRCodeScanned(data)
                FTQRCodeUtils.QRType.Offline -> onOfflineAuthQRCodeScanned(data)
                FTQRCodeUtils.QRType.Online -> onAuthQRCodeScanned(data)
                FTQRCodeUtils.QRType.Usernameless -> onAnonymousQRCodeScanned(data)
                FTQRCodeUtils.QRType.Invalid -> showErrorAlert("QR Code Error", IllegalStateException("Invalid QR code. Matches none of know types."))
            }
        }
    }

    private fun getAccounts(): List<FTAccount> =
        FuturaeSDK.client.accountApi.getActiveAccounts()

    protected suspend fun showToast(msg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(
            requireContext(),
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }
}
