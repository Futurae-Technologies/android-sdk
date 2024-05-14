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
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.AccountSelectionSheet
import com.futurae.futuraedemo.ui.activity.ActivityAccountHistory
import com.futurae.futuraedemo.ui.activity.AdaptiveViewerActivity
import com.futurae.futuraedemo.ui.activity.FTRQRCodeActivity
import com.futurae.futuraedemo.ui.activity.FuturaeActivity
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.Callback
import com.futurae.sdk.FuturaeCallback
import com.futurae.sdk.FuturaeResultCallback
import com.futurae.sdk.LockConfigurationType
import com.futurae.sdk.MalformedQRCodeException
import com.futurae.sdk.adaptive.AdaptiveSDK
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.exception.LockOperationIsLockedException
import com.futurae.sdk.model.AccountsMigrationResource
import com.futurae.sdk.model.AccountsStatus
import com.futurae.sdk.model.ApproveInfo
import com.futurae.sdk.model.MigrateableAccounts
import com.futurae.sdk.model.SessionInfo
import com.futurae.sdk.utils.QRCodeUtils
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import timber.log.Timber

/**
 * A fragment that performs common Futurae SDK operations. The lock fragments can inherit this so they
 * can implement only the lock/unlocking flow.
 */
abstract class FragmentSDKOperations : Fragment() {

    abstract fun toggleAdaptiveButton(): MaterialButton
    abstract fun viewAdaptiveCollectionsButton(): MaterialButton
    abstract fun setAdaptiveThreshold(): MaterialButton
    abstract fun serviceLogoButton(): MaterialButton

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
        toggleAdaptiveButton().setOnClickListener {
            if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) {
                FuturaeSdkWrapper.sdk.disableAdaptive()
                toggleAdaptiveButton().text = "Enable Adaptive"
            } else {
                FuturaeSdkWrapper.sdk.enableAdaptive(requireActivity().application)
                toggleAdaptiveButton().text = "Disable Adaptive"
            }
        }
        viewAdaptiveCollectionsButton().setOnClickListener {
            if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) {
                startActivity(
                    Intent(context, AdaptiveViewerActivity::class.java)
                )
            } else {
                showAlert("Adaptive", "Please enable Adaptive to see your collections.")
            }
        }
        toggleAdaptiveButton().text =
            if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) "Disable Adaptive" else "Enable Adaptive"

        setAdaptiveThreshold().setOnClickListener {
            if (FuturaeSdkWrapper.sdk.isAdaptiveEnabled()) {
                var sliderValue = AdaptiveSDK.getAdaptiveCollectionThreshold()
                val dialogView = layoutInflater.inflate(R.layout.dialog_adaptive_time, null)
                val textValue = dialogView.findViewById<TextView>(R.id.sliderValue).apply {
                    text = "$sliderValue sec"
                }
                dialogView.findViewById<Slider>(R.id.slider).apply {
                    value = sliderValue.toFloat()
                    addOnChangeListener { _, value, _ ->
                        sliderValue = value.toInt()
                        textValue.text = "${value.toInt()} sec"
                    }
                }
                val dialog = AlertDialog.Builder(
                    requireContext(),
                    com.google.android.material.R.style.Theme_Material3_Light_Dialog
                )
                    .setTitle("Adaptive time threshold").setView(dialogView)
                    .setPositiveButton("OK") { dialog, which ->
                        AdaptiveSDK.setAdaptiveCollectionThreshold(sliderValue)
                    }.create()
                dialog.show()
            } else {
                showAlert("Adaptive SDK", "Please initialize/enable Adaptive SDK first")
            }
        }

        serviceLogoButton().setOnClickListener {
            val ftAccounts = FuturaeSdkWrapper.client.accounts
            if (ftAccounts.isNotEmpty()) {
                val ftAccount = ftAccounts.first()
                val dialogView = layoutInflater.inflate(R.layout.dialog_service_logo, null)
                val imageView = dialogView.findViewById<ImageView>(R.id.logoImage)
                Glide.with(this).load(ftAccount.serviceLogo)
                        .placeholder(R.drawable.ic_content_copy)
                        .into(imageView)
                val dialog =
                    AlertDialog.Builder(
                        requireContext(),
                        com.google.android.material.R.style.Theme_Material3_Light_Dialog
                    )
                        .setTitle("Service Logo")
                        .setView(dialogView).setPositiveButton("OK") { dialog, which ->
                            dialog.dismiss()
                        }.create()
                dialog.show()
            } else {
                Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    protected fun getAccountHistory() {
        FuturaeSdkWrapper.client.accounts.firstOrNull()?.let {
            startActivity(Intent(requireContext(), ActivityAccountHistory::class.java))
        } ?: Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
    }

    protected fun getAccountsStatus() {
        FuturaeSdkWrapper.client.accounts.takeIf { it.isNotEmpty() }?.let { accounts ->
            FuturaeSdkWrapper.client.getAccountsStatus(accounts.map { it.userId },
                object : FuturaeResultCallback<AccountsStatus> {
                    override fun success(result: AccountsStatus) {
                        Toast.makeText(requireContext(), "Acc status success", Toast.LENGTH_SHORT).show()
                        val sessionInfos = result.statuses.first().sessionInfos
                        if (sessionInfos.isNotEmpty()) {
                            val session = ApproveSession(sessionInfos.first())
                            (activity as FuturaeActivity).onApproveAuth(
                                session,
                                session.hasExtraInfo(),
                                null
                            )
                        } else {
                            showAccountStatusInfo(result)
                        }
                    }

                    override fun failure(t: Throwable) {
                        showErrorAlert("Account Status Error", t)
                    }
                })
        } ?: Toast.makeText(requireContext(), "No accounts enrolled", Toast.LENGTH_SHORT).show()
    }

    private fun showAccountStatusInfo(result: AccountsStatus) {
        val serviceIdsEnrolled: List<String> =
            FuturaeSdkWrapper.client.accounts.map { it.serviceId }
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

    protected fun onLogout() {
        // For demo purposes, we simply logout the first account we can find
        val accounts = FuturaeSdkWrapper.client.accounts
        if (accounts.isEmpty()) {
            showAlert("Error", "No account to logout")
            return
        }
        val account = accounts[0]
        FuturaeSdkWrapper.client.logout(account.userId, object : FuturaeCallback {
            override fun success() {
                showAlert("Success", "Logged out " + account.username)
            }

            override fun failure(throwable: Throwable) {
                showErrorAlert("SDK Unlock", throwable)
            }
        })
    }

    protected fun onTOTPAuth() {
        val accounts = FuturaeSdkWrapper.client.accounts
        if (accounts.isEmpty()) {
            showAlert("Error", "No account enrolled")
            return
        }
        val account = accounts[0]
        try {
            val totp = FuturaeSdkWrapper.client.nextTotp(account.userId)
            showAlert(
                "TOTP", "Code: ${totp.passcode}\nRemaining seconds: ${totp.remainingSecs}"
            )
        } catch (e: LockOperationIsLockedException) {
            showErrorAlert("SDK Unlock", e)
        }
    }

    protected fun onHotpAuth() {
        val accounts = FuturaeSdkWrapper.client.accounts
        if (accounts.isEmpty()) {
            showAlert("Error", "No account enrolled")
            return
        }
        val account = accounts[0]
        try {
            val hotp = FuturaeSdkWrapper.client.getSynchronousAuthToken(account.userId)
            showDialog("TOTP", "HOTP JWT: ${hotp}\n", "OK", {
                Timber.e("JWT: ${hotp}")
                val clipboardMgr = getSystemService(requireContext(), ClipboardManager::class.java)
                val clip = ClipData.newPlainText("HOTP JWT", hotp)
                clipboardMgr?.setPrimaryClip(clip)
            })
        } catch (e: LockOperationIsLockedException) {
            showErrorAlert("SDK Unlock", e)
        }
    }

    protected fun attemptRestoreAccounts() {
        FuturaeSdkWrapper.client.checkForMigratableAccounts(object : Callback<MigrateableAccounts> {
            override fun onSuccess(result: MigrateableAccounts) {
                if (result.numAccounts > 0) {
                    showDialog(
                        "SDK Account Migration",
                        "Restoring Accounts possible for:\n ${result.numAccounts} accounts."
                                + "\n Requires PIN: ${result.pinProtected}"
                                + "\n Requires Adaptive: ${result.adaptiveEnabled}"
                                + "\n Account names: \n ${result.migratableAccountInfos.joinToString { acc -> acc.username + ",\n" }}",
                        "Proceed",
                        {
                            if (result.pinProtected
                                || localStorage.getPersistedSDKConfig().lockConfigurationType == LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL
                            ) {
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
            }

            override fun onError(throwable: Throwable) {
                showErrorAlert("SDK Unlock", throwable)
            }
        })
    }


    private fun onAccountsMigrationExecute() {
        FuturaeSdkWrapper.client.executeAccountMigration(object :
            FuturaeResultCallback<Array<AccountsMigrationResource.MigrationAccount>> {
            override fun success(result: Array<AccountsMigrationResource.MigrationAccount>) {
                val userIdsMigrated = StringBuilder()
                for (i in result.indices) {
                    if (i > 0) {
                        userIdsMigrated.append("\n")
                    }
                    userIdsMigrated.append(result[i].username)
                }
                Timber.i("Accounts migration successful. Accounts migrated (total: " + result.size + "): " + userIdsMigrated)
                showAlert(
                    "Accounts migration successful",
                    """Accounts migrated (total: ${result.size}):$userIdsMigrated""".trimIndent()
                )
            }

            override fun failure(throwable: Throwable) {
                showErrorAlert("SDK Unlock", throwable)
            }
        })
    }

    private fun onAccountsMigrationWithSDKPINExecute() {
        getPinWithCallback {
            FuturaeSdkWrapper.client.executeAccountMigrationWithSDKPin(it,
                object : Callback<List<AccountsMigrationResource.MigrationAccount>> {
                    override fun onSuccess(result: List<AccountsMigrationResource.MigrationAccount>) {
                        showAlert(
                            "Accounts migration successful",
                            "Accounts migrated (total: ${result.size}): \n ${result.joinToString { acc -> acc.username + ",\n" }}".trimIndent()
                        )
                    }

                    override fun onError(throwable: Throwable) {
                        showErrorAlert("SDK Unlock", throwable)
                    }
                })
        }
    }

    // QRCode callbacks
    private fun onEnrollQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
            FuturaeSdkWrapper.client.enroll(qrcode.rawValue, object : FuturaeCallback {
                override fun success() {
                    Timber.i("Enrollment successful")
                    showAlert("Success", "Enrollment successful")
                }

                override fun failure(throwable: Throwable) {
                    showErrorAlert("SDK Unlock", throwable)
                }
            })
        }
    }

    protected fun onManualEntryEnroll(sdkPin: CharArray? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_entry, null, false)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("ok") { dialog, which ->
                val activationShortCode =
                    dialogView.findViewById<AppCompatEditText>(R.id.edittext).text.toString().replace(" ", "")
                if (sdkPin != null) {
                    FuturaeSdkWrapper.client.enrollWithShortcodeAndSetupSDKPin(
                        activationShortCode,
                        sdkPin,
                        object : Callback<Unit> {
                            override fun onSuccess(result: Unit) {
                                Timber.i("Enrollment successful")
                                showAlert("Success", "Enrollment successful")
                            }

                            override fun onError(throwable: Throwable) {
                                showErrorAlert("SDK Unlock", throwable)
                            }
                        })
                } else {
                    FuturaeSdkWrapper.client.enrollWithShortcode(activationShortCode, object : FuturaeCallback {
                        override fun success() {
                            Timber.i("Enrollment successful")
                            showAlert("Success", "Enrollment successful")
                        }

                        override fun failure(throwable: Throwable) {
                            showErrorAlert("SDK Unlock", throwable)
                        }
                    })
                }
            }
            .create()
        dialog.show()
    }

    private fun onAuthQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
            var userId: String? = null
            var sessionToken: String? = null
            try {
                userId = QRCodeUtils.getUserIdFromQrcode(qrcode.rawValue)
                sessionToken = QRCodeUtils.getSessionTokenFromQrcode(qrcode.rawValue)
            } catch (e: MalformedQRCodeException) {
                e.printStackTrace()
                return
            }

            FuturaeSdkWrapper.client.sessionInfoByToken(
                userId,
                sessionToken,
                object : FuturaeResultCallback<SessionInfo> {
                    override fun success(result: SessionInfo) {
                        ApproveSession(result).takeIf { it.userId?.isNotBlank() == true }
                            ?.let { session ->
                                showDialog("approve",
                                    "Would you like to approve the request?${session.toDialogMessage()}",
                                    "Approve",
                                    {
                                        FuturaeSdkWrapper.client.approveAuth(
                                            qrcode.rawValue,
                                            object : FuturaeCallback {
                                                override fun success() {
                                                    Toast.makeText(
                                                        requireContext(),
                                                        "Approved",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }

                                                override fun failure(throwable: Throwable) {
                                                    showErrorAlert("SDK Error", throwable)
                                                }
                                            },
                                            null
                                        )
                                    },
                                    "Deny",
                                    {
                                        FuturaeSdkWrapper.client.rejectAuth(
                                            qrcode.rawValue,
                                            false,
                                            object : FuturaeCallback {
                                                override fun success() {
                                                    Toast.makeText(
                                                        requireContext(),
                                                        "Denied",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }

                                                override fun failure(throwable: Throwable) {
                                                    showErrorAlert("SDK Error", throwable)
                                                }
                                            }, null
                                        )
                                    })
                            } ?: showErrorAlert(
                            "SDK Error",
                            Throwable("Session is missing user id")
                        )
                    }

                    override fun failure(t: Throwable) {
                        showErrorAlert("SDK Unlock", t)
                    }
                })
        }
    }

    protected fun onAnonymousQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { barcode ->
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
        val sessionToken = QRCodeUtils.getSessionTokenFromQrcode(qrCode)
        FuturaeSdkWrapper.client.sessionInfoByToken(
            userId,
            sessionToken,
            object : FuturaeResultCallback<SessionInfo> {
                override fun success(sessionInfo: SessionInfo) {
                    val session = ApproveSession(sessionInfo)
                    showDialog("approve",
                        "Would you like to approve the request?${session.toDialogMessage()}",
                        "Approve",
                        {
                            FuturaeSdkWrapper.sdk.getClient().approveAuthWithUsernamelessQrCode(
                                qrCode,
                                userId,
                                session.info,
                                object : Callback<Unit> {
                                    override fun onSuccess(result: Unit) {
                                        Toast.makeText(requireContext(), "Approved", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onError(throwable: Throwable) {
                                        showErrorAlert("SDK Error", throwable)
                                    }

                                },
                            )
                        },
                        "Reject",
                        {
                            FuturaeSdkWrapper.sdk.getClient().rejectAuthWithUsernamelessQrCode(
                                qrCode,
                                userId,
                                false,
                                session.info,
                                object : Callback<Unit> {
                                    override fun onSuccess(result: Unit) {
                                        Toast.makeText(requireContext(), "Rejected", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onError(throwable: Throwable) {
                                        showErrorAlert("SDK Error", throwable)
                                    }
                                },
                            )
                        }
                    )
                }

                override fun failure(t: Throwable) {
                    showErrorAlert("SDK Unlock", t)
                }
            })
    }

    protected fun onOfflineAuthQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { barcode ->
            val qrCode = barcode.rawValue
            val extras: List<ApproveInfo>? = try {
                QRCodeUtils.getExtraInfoFromOfflineQrcode(qrCode)
            } catch (e: MalformedQRCodeException) {
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
                val verificationSignature = try {
                    FuturaeSdkWrapper.client.computeVerificationCodeFromQrcode(qrCode)
                } catch (e: Exception) {
                    showErrorAlert("SDK Unlock", e)
                    return@showDialog
                }
                showAlert(
                    "Confirm Transaction",
                    "To Approve the transaction, enter: $verificationSignature in the browser"
                )
            }, " Deny", {
                //Nothing
            })
        }
    }

    protected fun onQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
            when (QRCodeUtils.getQrcodeType(qrcode.rawValue)) {
                QRCodeUtils.QRType.Enroll -> onEnrollQRCodeScanned(data)
                QRCodeUtils.QRType.Offline -> onOfflineAuthQRCodeScanned(data)
                QRCodeUtils.QRType.Online -> onAuthQRCodeScanned(data)
                QRCodeUtils.QRType.Usernameless -> onAnonymousQRCodeScanned(data)
            }
        }
    }

    protected fun getPinWithCallback(callback: (CharArray) -> Unit) {
        val pinFragment = FragmentPin()
        parentFragmentManager.beginTransaction().add(R.id.pinFragmentContainer, pinFragment.apply {
            listener = object : FragmentPin.Listener {
                override fun onPinComplete(pin: CharArray) {
                    parentFragmentManager.beginTransaction().remove(pinFragment).commit()
                    callback(pin)
                }
            }
        }).commit()
    }
}