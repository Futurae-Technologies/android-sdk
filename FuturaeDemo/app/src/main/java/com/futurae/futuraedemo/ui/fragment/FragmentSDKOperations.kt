package com.futurae.futuraedemo.ui.fragment

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.futurae.futuraedemo.ui.activity.FTRQRCodeActivity
import com.futurae.futuraedemo.ui.showAlert
import com.futurae.futuraedemo.ui.showDialog
import com.futurae.futuraedemo.ui.showErrorAlert
import com.futurae.futuraedemo.ui.toDialogMessage
import com.futurae.sdk.*
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.exception.LockOperationIsLockedException
import com.futurae.sdk.model.AccountsMigrationResource.MigrationAccount
import com.futurae.sdk.model.ApproveInfo
import com.futurae.sdk.model.SessionInfo
import com.google.android.gms.vision.barcode.Barcode
import timber.log.Timber

/**
 * A fragment that performs common Futurae SDK operations. The lock fragments can inherit this so they
 * can implement only the lock/unlocking flow.
 */
abstract class FragmentSDKOperations : Fragment() {

    private val getQRCodeCallback =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    onQRCodeScanned(intent)
                } ?: throw IllegalStateException("Activity result without Intent")
            }
        }

    protected fun scanQRCode() {
        getQRCodeCallback.launch(
            FTRQRCodeActivity.getIntent(requireContext(), true, false),
        )
    }

    protected fun onLogout() {
        // For demo purposes, we simply logout the first account we can find
        val accounts = FuturaeSDK.INSTANCE.getClient().accounts
        if (accounts == null || accounts.size == 0) {
            showAlert("Error", "No account to logout")
            return
        }
        val account = accounts[0]
        FuturaeSDK.INSTANCE.getClient().logout(account.userId,
            object : FuturaeCallback {
                override fun success() {
                    showAlert("Success", "Logged out " + account.username)
                }

                override fun failure(throwable: Throwable) {
                    showErrorAlert("SDK Unlock", throwable)
                }
            })
    }

    protected fun onTOTPAuth() {
        val accounts = FuturaeSDK.INSTANCE.getClient().accounts
        if (accounts == null || accounts.size == 0) {
            showAlert("Error", "No account enrolled")
            return
        }
        val account = accounts[0]
        try {
            val totp = FuturaeSDK.INSTANCE.getClient().nextTotp(account.userId)
            showAlert(
                "TOTP",
                "Code: ${totp.getPasscode()}\nRemaining seconds: ${totp.getRemainingSecs()}"
            )
        } catch (e: LockOperationIsLockedException) {
            showErrorAlert("SDK Unlock", e)
        }

    }

    protected fun onAccountsMigrationCheck() {
        FuturaeSDK.INSTANCE.getClient().checkAccountMigrationPossible(
            object : FuturaeResultCallback<Int> {
                override fun success(numAccounts: Int) {
                    if (numAccounts > 0) {
                        showAlert(
                            "Success",
                            "Accounts Migration is possible.\nNumber of accounts that can be migrated: $numAccounts"
                        )
                    } else {
                        showAlert(
                            "Info",
                            "Accounts Migration is not possible\nNo accounts found that can be migrated"
                        )
                    }
                }

                override fun failure(throwable: Throwable) {
                    showErrorAlert("SDK Unlock", throwable)
                }
            })
    }


    protected fun onAccountsMigrationExecute() {
        FuturaeSDK.INSTANCE.getClient().executeAccountMigration(
            object : FuturaeResultCallback<Array<MigrationAccount>> {
                override fun success(result: Array<MigrationAccount>) {
                    val userIdsMigrated = StringBuilder()
                    for (i in result.indices) {
                        if (i > 0) {
                            userIdsMigrated.append("\n")
                        }
                        userIdsMigrated.append(result[i].username)
                    }
                    showAlert(
                        "Accounts migration successful",
                        """Accounts migrated (total: ${result.size}):$userIdsMigrated""".trimIndent()
                    )
                }

                override fun failure(throwable: Throwable) {
                    showErrorAlert("SDK Unlock", throwable)
                }
            }
        )
    }

    // QRCode callbacks
    private fun onEnrollQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
            FuturaeSDK.INSTANCE.getClient().enroll(
                qrcode.rawValue,
                object : FuturaeCallback {
                    override fun success() {
                        showAlert("Success", "Enrollment successful")
                    }

                    override fun failure(throwable: Throwable) {
                        showErrorAlert("SDK Unlock", throwable)
                    }
                })
        }
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
            FuturaeSDK.INSTANCE.getClient().sessionInfoByToken(userId, sessionToken,
                object : FuturaeResultCallback<SessionInfo?> {
                    override fun success(sessionInfo: SessionInfo?) {
                        val session = ApproveSession(sessionInfo)
                        showDialog(
                            "approve",
                            "Would you like to approve the request?${session.toDialogMessage()}",
                            "Approve",
                            {
                                FuturaeSDK.INSTANCE.getClient().approveAuth(
                                    session.userId,
                                    session.sessionId, object : FuturaeCallback {
                                        override fun success() {
                                            Toast.makeText(
                                                requireContext(),
                                                "Approved",
                                                Toast.LENGTH_SHORT
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
                                FuturaeSDK.INSTANCE.getClient().rejectAuth(
                                    session.userId,
                                    session.sessionId, false, object : FuturaeCallback {
                                        override fun success() {
                                            Toast.makeText(
                                                requireContext(),
                                                "Denied",
                                                Toast.LENGTH_SHORT
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
                    FuturaeSDK.INSTANCE.getClient().computeVerificationCodeFromQrcode(qrCode)
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
            when (FuturaeClient.getQrcodeType(qrcode.rawValue)) {
                FuturaeClient.QR_ENROLL -> onEnrollQRCodeScanned(data)
                FuturaeClient.QR_ONLINE -> onAuthQRCodeScanned(data)
                FuturaeClient.QR_OFFLINE -> onOfflineAuthQRCodeScanned(data)
            }
        }
    }
}