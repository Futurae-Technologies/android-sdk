package com.futurae.futuraedemo.ui.fragment

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.AccountSelectionSheet
import com.futurae.futuraedemo.ui.activity.ActivityAccountHistory
import com.futurae.futuraedemo.ui.activity.FTRQRCodeActivity
import com.futurae.futuraedemo.ui.activity.FuturaeActivity
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.Callback
import com.futurae.sdk.FuturaeCallback
import com.futurae.sdk.FuturaeClient
import com.futurae.sdk.FuturaeResultCallback
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.LockConfigurationType
import com.futurae.sdk.MalformedQRCodeException
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.exception.LockOperationIsLockedException
import com.futurae.sdk.model.AccountsMigrationResource
import com.futurae.sdk.model.AccountsStatus
import com.futurae.sdk.model.ApproveInfo
import com.futurae.sdk.model.MigrateableAccounts
import com.futurae.sdk.model.SessionInfo
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.material.button.MaterialButton
import timber.log.Timber

/**
 * A fragment that performs common Futurae SDK operations. The lock fragments can inherit this so they
 * can implement only the lock/unlocking flow.
 */
abstract class FragmentSDKOperations : Fragment() {

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
        serviceLogoButton().setOnClickListener {
            val ftAccounts = FuturaeSDK.INSTANCE.client.accounts
            if (ftAccounts.isNotEmpty()) {
                val ftAccount = ftAccounts.first()
                val dialogView = layoutInflater.inflate(R.layout.dialog_service_logo, null)
                val imageView = dialogView.findViewById<ImageView>(R.id.logoImage)
                Glide.with(this).load(ftAccount.serviceLogo).placeholder(R.drawable.ic_settings).into(imageView)
                val dialog =
                    AlertDialog.Builder(
                        requireContext(),
                        com.google.android.material.R.style.Theme_Material3_Light_Dialog
                    ).setTitle("Service Logo")
                        .setView(dialogView).setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }.create()
                dialog.show()
            } else {
                Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
            }
        }

    }

    protected fun getAccountHistory() {
        FuturaeSDK.INSTANCE.client.accounts.firstOrNull()?.let {
            startActivity(Intent(requireContext(), ActivityAccountHistory::class.java))
        } ?: Toast.makeText(requireContext(), "No account enrolled", Toast.LENGTH_SHORT).show()
    }

    protected fun getAccountsStatus() {
        FuturaeSDK.INSTANCE.client.accounts.takeIf { it.isNotEmpty() }?.let { accounts ->
            FuturaeSDK.INSTANCE.client.getAccountsStatus(accounts.map { it.userId },
                object : FuturaeResultCallback<AccountsStatus> {
                    override fun success(p0: AccountsStatus) {
                        Toast.makeText(requireContext(), "Acc status success", Toast.LENGTH_SHORT).show()
                        val sessionInfos = p0.statuses.first().sessionInfos
                        if (sessionInfos.isNotEmpty()) {
                            val session = ApproveSession(sessionInfos.first())
                            (activity as FuturaeActivity).onApproveAuth(session, session.hasExtraInfo())
                        }
                    }

                    override fun failure(t: Throwable) {
                        showErrorAlert("Account Status Error", t)
                    }
                })
        } ?: Toast.makeText(requireContext(), "No accounts enrolled", Toast.LENGTH_SHORT).show()
    }


    protected fun scanQRCode() {
        getQRCodeCallback.launch(
            FTRQRCodeActivity.getIntent(requireContext(), true, false),
        )
    }

    protected fun onLogout() {
        // For demo purposes, we simply logout the first account we can find
        val accounts = FuturaeSDK.INSTANCE.client.accounts
        if (accounts == null || accounts.size == 0) {
            showAlert("Error", "No account to logout")
            return
        }
        val account = accounts[0]
        FuturaeSDK.INSTANCE.client.logout(account.userId, object : FuturaeCallback {
            override fun success() {
                showAlert("Success", "Logged out " + account.username)
            }

            override fun failure(throwable: Throwable) {
                showErrorAlert("SDK Unlock", throwable)
            }
        })
    }

    protected fun onTOTPAuth() {
        val accounts = FuturaeSDK.INSTANCE.client.accounts
        if (accounts == null || accounts.size == 0) {
            showAlert("Error", "No account enrolled")
            return
        }
        val account = accounts[0]
        try {
            val totp = FuturaeSDK.INSTANCE.client.nextTotp(account.userId)
            showAlert(
                "TOTP", "Code: ${totp.getPasscode()}\nRemaining seconds: ${totp.getRemainingSecs()}"
            )
        } catch (e: LockOperationIsLockedException) {
            Timber.e(e)
            showErrorAlert("SDK Unlock", e)
        }
    }

    protected fun onHotpAuth() {
        val accounts = FuturaeSDK.INSTANCE.client.accounts
        if (accounts == null || accounts.size == 0) {
            showAlert("Error", "No account enrolled")
            return
        }
        val account = accounts[0]
        try {
            val hotp = FuturaeSDK.INSTANCE.client.getSynchronousAuthToken(account.userId)
            showDialog("TOTP", "HOTP JWT: ${hotp}\n", "OK", {
                Timber.e("JWT: ${hotp}")
                val clipboardMgr = getSystemService(requireContext(), ClipboardManager::class.java)
                val clip = ClipData.newPlainText("HOTP JWT", hotp)
                clipboardMgr?.setPrimaryClip(clip)
            })
        } catch (e: LockOperationIsLockedException) {
            Timber.e(e)
            showErrorAlert("SDK Unlock", e)
        }
    }

    protected fun attemptRestoreAccounts() {
        FuturaeSDK.INSTANCE.client.checkForMigratableAccounts(object : Callback<MigrateableAccounts> {
            override fun onSuccess(result: MigrateableAccounts) {
                if (result.numAccounts > 0) {
                    showDialog(
                        "SDK Account Migration",
                        "Restoring Accounts possible for:\n ${result.numAccounts} accounts."
                                + "\n Requires PIN: ${result.pinProtected}"
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
                Timber.e(throwable)
                showErrorAlert("SDK Unlock", throwable)
            }
        })
    }


    private fun onAccountsMigrationExecute() {
        FuturaeSDK.INSTANCE.client.executeAccountMigration(object :
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
                Timber.e(throwable.localizedMessage)
                showErrorAlert("SDK Unlock", throwable)
            }
        })
    }

    private fun onAccountsMigrationWithSDKPINExecute() {
        getPinWithCallback {
            FuturaeSDK.INSTANCE.client.executeAccountMigrationWithSDKPin(it,
                object : Callback<List<AccountsMigrationResource.MigrationAccount>> {
                    override fun onSuccess(result: List<AccountsMigrationResource.MigrationAccount>) {
                        showAlert(
                            "Accounts migration successful",
                            "Accounts migrated (total: ${result.size}): \n ${result.joinToString { acc -> acc.username + ",\n" }}".trimIndent()
                        )
                    }

                    override fun onError(throwable: Throwable) {
                        Timber.e(throwable.localizedMessage)
                        showErrorAlert("SDK Unlock", throwable)
                    }
                })
        }
    }

    // QRCode callbacks
    private fun onEnrollQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
            FuturaeSDK.INSTANCE.client.enroll(qrcode.rawValue, object : FuturaeCallback {
                override fun success() {
                    Timber.i("Enrollment successful")
                    showAlert("Success", "Enrollment successful")
                }

                override fun failure(throwable: Throwable) {
                    Timber.e("Enrollment failed: " + throwable.localizedMessage)
                    showErrorAlert("SDK Unlock", throwable)
                }
            })
        }
    }

    protected fun onManualEntryEnroll(sdkPin: CharArray? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_entry, null, false)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("ok") { _, _ ->
                val activationShortCode =
                    dialogView.findViewById<AppCompatEditText>(R.id.edittext).text.toString().replace(" ", "")
                if (sdkPin != null) {
                    FuturaeSDK.INSTANCE.client.enrollWithShortcodeAndSetupSDKPin(
                        activationShortCode,
                        sdkPin,
                        object : Callback<Unit> {
                            override fun onSuccess(result: Unit) {
                                Timber.i("Enrollment successful")
                                showAlert("Success", "Enrollment successful")
                            }

                            override fun onError(throwable: Throwable) {
                                Timber.e("Enrollment failed: " + throwable.localizedMessage)
                                showErrorAlert("SDK Unlock", throwable)
                            }
                        })
                } else {
                    FuturaeSDK.INSTANCE.client.enrollWithShortcode(activationShortCode, object : FuturaeCallback {
                        override fun success() {
                            Timber.i("Enrollment successful")
                            showAlert("Success", "Enrollment successful")
                        }

                        override fun failure(throwable: Throwable) {
                            Timber.e("Enrollment failed: " + throwable.localizedMessage)
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
                userId = FuturaeClient.getUserIdFromQrcode(qrcode.rawValue)
                sessionToken = FuturaeClient.getSessionTokenFromQrcode(qrcode.rawValue)
            } catch (e: MalformedQRCodeException) {
                e.printStackTrace()
                return
            }

            FuturaeSDK.INSTANCE.client.sessionInfoByToken(
                userId,
                sessionToken,
                object : FuturaeResultCallback<SessionInfo?> {
                    override fun success(sessionInfo: SessionInfo?) {
                        if (sessionInfo == null) {
                            showDialog(
                                "Something went wrong",
                                "Please try again",
                                "Ok",
                                { })
                            return
                        }

                        val session = ApproveSession(sessionInfo)
                        showDialog("approve",
                            "Would you like to approve the request?${session.toDialogMessage()}",
                            "Approve",
                            {
                                FuturaeSDK.INSTANCE.client.approveAuth(
                                    session.userId, session.sessionId, object : FuturaeCallback {
                                        override fun success() {
                                            Toast.makeText(
                                                requireContext(), "Approved", Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        override fun failure(t: Throwable) {
                                            Timber.e(t)
                                        }
                                    }, session.info
                                )
                            },
                            "Deny",
                            {
                                FuturaeSDK.INSTANCE.client.rejectAuth(
                                    session.userId, session.sessionId, false, object : FuturaeCallback {
                                        override fun success() {
                                            Toast.makeText(
                                                requireContext(), "Denied", Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        override fun failure(t: Throwable) {
                                            Timber.e(t)
                                        }
                                    }, session.info
                                )
                            })
                    }

                    override fun failure(t: Throwable) {
                        Timber.e(t)
                        showErrorAlert("SDK Unlock", t)
                    }
                })
        }
    }

    protected fun onOfflineAuthQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { barcode ->
            val qrCode = barcode.rawValue
            val extras: Array<ApproveInfo>? = try {
                FuturaeClient.getExtraInfoFromOfflineQrcode(qrCode)
            } catch (e: MalformedQRCodeException) {
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
                val verificationSignature = try {
                    FuturaeSDK.INSTANCE.client.computeVerificationCodeFromQrcode(qrCode)
                } catch (e: Exception) {
                    Timber.e(e)
                    showErrorAlert("SDK Unlock", e)
                    return@showDialog
                }
                showAlert(
                    "Confirm Transaction", "To Approve the transaction, enter: $verificationSignature in the browser"
                )
            }, " Deny", {
                //Nothing
            })
        }
    }

    protected fun onAnonymousQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { barcode ->
            val modalBottomSheet = AccountSelectionSheet().apply {
                listener = object : AccountSelectionSheet.Listener {
                    override fun onAccountSelected(userId: String) {
                        approveUsernamelessQr(barcode.rawValue, userId)
                    }
                }
            }
            modalBottomSheet.show(childFragmentManager, AccountSelectionSheet.TAG)
        }
    }

    private fun approveUsernamelessQr(qrCode: String, userId: String) {
        val sessionToken = FuturaeClient.getSessionTokenFromQrcode(qrCode)
        FuturaeSDK.INSTANCE.client.sessionInfoByToken(
            userId,
            sessionToken,
            object : FuturaeResultCallback<SessionInfo> {
                override fun success(sessionInfo: SessionInfo) {
                    val session = ApproveSession(sessionInfo)
                    showDialog("approve",
                        "Would you like to approve the request?${session.toDialogMessage()}",
                        "Approve",
                        {
                            FuturaeSDK.INSTANCE.client.approveAuthWithUsernamelessQrCode(
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
                        "Deny",
                        {
                            FuturaeSDK.INSTANCE.client.rejectAuthWithUsernamelessQrCode(
                                qrCode,
                                userId,
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
                        })
                }

                override fun failure(t: Throwable) {
                    showErrorAlert("SDK Unlock", t)
                }
            })
    }

    protected fun onQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
            when (FuturaeClient.getQrcodeType(qrcode.rawValue)) {
                FuturaeClient.QR_ENROLL -> onEnrollQRCodeScanned(data)
                FuturaeClient.QR_ONLINE -> onAuthQRCodeScanned(data)
                FuturaeClient.QR_OFFLINE -> onOfflineAuthQRCodeScanned(data)
                FuturaeClient.QR_USERNAMELESS -> onAnonymousQRCodeScanned(data)
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