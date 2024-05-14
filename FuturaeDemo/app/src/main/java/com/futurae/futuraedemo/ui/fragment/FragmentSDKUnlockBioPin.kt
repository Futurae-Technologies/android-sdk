package com.futurae.futuraedemo.ui.fragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockBioPinBinding
import com.futurae.futuraedemo.ui.activity.FTRQRCodeActivity
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.Callback
import com.futurae.sdk.MalformedQRCodeException
import com.futurae.sdk.exception.LockOperationIsLockedException
import com.futurae.sdk.model.ApproveInfo
import com.futurae.sdk.model.CurrentTotp
import com.futurae.sdk.model.UserPresenceVerification
import com.futurae.sdk.utils.QRCodeUtils
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.material.button.MaterialButton

/**
 * This is the most complex UI code, because it doesn't use FragmentSDKOperations for scanning QR codes.
 * This Fragment has to manage the state of scanned QR codes, provided PIN and current SDK operation flow.
 */
class FragmentSDKUnlockBioPin : FragmentSDKLockedFragment() {

    private lateinit var binding: FragmentSdkUnlockBioPinBinding

    private var currentRequest = 0

    companion object {
        const val REQUEST_ENROLL_WITH_PIN = 10001
        const val REQUEST_QR_OFFLINE_WITH_PIN = 10004
        const val REQUEST_QR_OFFLINE_WITH_BIO = 10006
    }

    private val getQRCodeCallback =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                (result.data?.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
                    when (currentRequest) {
                        REQUEST_ENROLL_WITH_PIN -> {
                            if (QRCodeUtils.getQrcodeType(qrcode.rawValue) == QRCodeUtils.QRType.Enroll) {
                                getPinWithCallback {
                                    FuturaeSdkWrapper.client.enrollAndSetupSDKPin(
                                        qrcode.rawValue,
                                        it,
                                        object : Callback<Unit> {
                                            override fun onSuccess(result: Unit) {
                                                showAlert("SDK Unlock", "Enrollment successful")
                                                onUnlocked(
                                                    binding.textTimerValue,
                                                    binding.textStatusValue
                                                )
                                                currentRequest = 0
                                            }

                                            override fun onError(throwable: Throwable) {
                                                showErrorAlert("SDK Unlock", throwable)
                                                onLocked(
                                                    binding.textTimerValue,
                                                    binding.textStatusValue
                                                )
                                                currentRequest = 0
                                            }
                                        }
                                    )
                                }
                            } else {
                                showErrorAlert(
                                    "SDK Unlock",
                                    IllegalStateException("Invalid QR code for Enroll")
                                )
                                currentRequest = 0
                            }
                        }

                        REQUEST_QR_OFFLINE_WITH_PIN -> {
                            if (QRCodeUtils.getQrcodeType(qrcode.rawValue) == QRCodeUtils.QRType.Offline) {
                                val accounts = FuturaeSdkWrapper.client.accounts
                                if (accounts.isEmpty()) {
                                    showAlert("SDK Unlock", "No account enrolled")
                                    currentRequest = 0
                                } else {
                                    getPinWithCallback {
                                        try {
                                            val extras: List<ApproveInfo>? = try {
                                                QRCodeUtils.getExtraInfoFromOfflineQrcode(qrcode.rawValue)
                                            } catch (e: MalformedQRCodeException) {
                                                showErrorAlert("SDK Unlock", e)
                                                currentRequest = 0
                                                null
                                            }
                                            val sb = StringBuffer()
                                            if (extras != null) {
                                                sb.append("\n")
                                                for (info in extras) {
                                                    sb.append(info.key).append(": ").append(info.value)
                                                        .append("\n")
                                                }
                                            }
                                            showDialog("Approve", "Request Info: $sb", "Approve", {
                                                val verificationSignature = try {
                                                    FuturaeSdkWrapper.client
                                                        .computeVerificationCodeFromQrcode(
                                                            qrcode.rawValue,
                                                            it
                                                        )
                                                } catch (e: Exception) {
                                                    showErrorAlert("SDK Unlock", e)
                                                }
                                                currentRequest = 0
                                                showAlert(
                                                    "Confirm Transaction",
                                                    "To Approve the transaction, enter: $verificationSignature in the browser"
                                                )
                                            }, " Deny", {
                                                //Nothing
                                            })
                                        } catch (e: LockOperationIsLockedException) {
                                            showErrorAlert("SDK Unlock", e)
                                            currentRequest = 0
                                        }
                                    }
                                }
                            } else {
                                showErrorAlert(
                                    "SDK Unlock",
                                    IllegalStateException("Invalid QR code for Offline Auth")
                                )
                                currentRequest = 0
                            }
                        }

                        REQUEST_QR_OFFLINE_WITH_BIO -> {
                            if (QRCodeUtils.getQrcodeType(qrcode.rawValue) == QRCodeUtils.QRType.Offline) {
                                val qrCode = qrcode.rawValue

                                val extras: List<ApproveInfo>? = try {
                                    QRCodeUtils.getExtraInfoFromOfflineQrcode(qrCode)
                                } catch (e: MalformedQRCodeException) {
                                    showErrorAlert("SDK Unlock", e)
                                    currentRequest = 0
                                    null
                                }
                                val sb = StringBuffer()
                                if (extras != null) {
                                    sb.append("\n")
                                    for (info in extras) {
                                        sb.append(info.key)
                                            .append(": ")
                                            .append(info.value)
                                            .append("\n")
                                    }
                                }
                                showDialog(
                                    "Approve",
                                    "Request Info: ${sb}",
                                    "Approve with biometrics",
                                    {
                                        FuturaeSdkWrapper.client
                                            .computeVerificationCodeFromQrcodeWithBiometrics(
                                                qrCode,
                                                requireActivity(),
                                                "Bio auth",
                                                "Authenticate",
                                                "Authenticate to unlock PIN",
                                                "cancel",
                                                object : Callback<String> {
                                                    override fun onSuccess(result: String) {
                                                        showDialog(
                                                            "Approve",
                                                            "Request Info: ${sb}",
                                                            "Approve",
                                                            {
                                                                showAlert(
                                                                    "Confirm Transaction",
                                                                    "To Approve the transaction, enter: $result in the browser"
                                                                )
                                                            },
                                                            " Deny",
                                                            {
                                                                //Nothing
                                                            })
                                                        currentRequest = 0
                                                    }

                                                    override fun onError(throwable: Throwable) {
                                                        showErrorAlert("Error", throwable)
                                                        currentRequest = 0
                                                    }
                                                }
                                            )
                                    })
                            } else {
                                showErrorAlert(
                                    "SDK Unlock",
                                    IllegalStateException("Invalid QR code for Enroll")
                                )
                                currentRequest = 0
                            }
                        }
                        else -> {
                            throw IllegalStateException("Unknown use case for QR: ${currentRequest}")
                        }
                    }
                } ?: throw IllegalStateException("Activity result without Intent")
            }
        }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSdkUnlockBioPinBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonUnlockWithBiometrics.setOnClickListener {
            FuturaeSdkWrapper.client.unlockWithBiometrics(
                requireActivity(),
                "Unlock SDK",
                "Authenticate with biometrics",
                "Authentication is required to unlock SDK operations",
                "cancel",
                object : Callback<UserPresenceVerification> {
                    override fun onSuccess(result: UserPresenceVerification) {
                        onUnlocked(binding.textTimerValue, binding.textStatusValue)
                    }

                    override fun onError(throwable: Throwable) {
                        showErrorAlert("SDK Unlock", throwable)
                    }
                }
            )
        }
        binding.buttonLock.setOnClickListener {
            FuturaeSdkWrapper.client.lock()
            onLocked(binding.textTimerValue, binding.textStatusValue)
        }
        binding.buttonChangePin.setOnClickListener {
            getPinWithCallback {
                FuturaeSdkWrapper.client.changeSDKPin(it, object : Callback<Unit> {
                    override fun onSuccess(result: Unit) {
                        Toast.makeText(
                            requireContext(),
                            "SDK PIN changed",
                            Toast.LENGTH_LONG
                        ).show()
                        currentRequest = 0
                    }

                    override fun onError(throwable: Throwable) {
                        showErrorAlert("SDK Unlock", throwable)
                    }
                })
            }
        }
        binding.buttonUnlockWithPin.setOnClickListener {
            getPinWithCallback {
                FuturaeSdkWrapper.client.unlockWithSDKPin(it, object : Callback<UserPresenceVerification> {
                    override fun onSuccess(result: UserPresenceVerification) {
                        onUnlocked(binding.textTimerValue, binding.textStatusValue)
                    }

                    override fun onError(throwable: Throwable) {
                        onLocked(binding.textTimerValue, binding.textStatusValue)
                        showErrorAlert("SDK Unlock", throwable)
                    }
                })
            }
        }
        binding.buttonEnroll.setOnClickListener {
            scanQRCode()
        }
        binding.buttonEnrollManual.setOnClickListener {
            onManualEntryEnroll()
        }
        binding.buttonQRCode.setOnClickListener {
            scanQRCode()
        }
        binding.buttonEnrollWithPin.setOnClickListener {
            currentRequest = REQUEST_ENROLL_WITH_PIN
            getQRCodeCallback.launch(
                FTRQRCodeActivity.getIntent(requireContext(), true, false),
            )
        }
        binding.buttonEnrollManualWithPin.setOnClickListener {
            getPinWithCallback {
                onManualEntryEnroll(it)
            }
        }
        binding.buttonTotp.setOnClickListener {
            onTOTPAuth()
        }
        binding.buttonTotpOffline.setOnClickListener {
            getPinWithCallback {
                val accounts = FuturaeSdkWrapper.client.accounts
                if (accounts.isEmpty()) {
                    showAlert("SDK Unlock", "No account enrolled")
                } else {
                    val account = accounts[0]
                    try {
                        val totp =
                            FuturaeSdkWrapper.client.nextTotp(account.userId, it)
                        showAlert(
                            "TOTP",
                            "Code: ${totp.passcode}\nRemaining seconds: ${totp.remainingSecs}"
                        )
                    } catch (e: LockOperationIsLockedException) {
                        showErrorAlert("SDK Unlock", e)
                    }
                }
            }
        }
        binding.buttonQRCodeWithPin.setOnClickListener {
            currentRequest = REQUEST_QR_OFFLINE_WITH_PIN
            getQRCodeCallback.launch(
                FTRQRCodeActivity.getIntent(requireContext(), true, false),
            )
        }
        binding.buttonActivateBiometrics.setOnClickListener {
            FuturaeSdkWrapper.client.activateBiometrics(
                requireActivity(),
                "Unlock SDK",
                "Activate biometrics",
                "Authenticate to enable biometric authentication unlocking",
                "cancel",
                object : Callback<Unit> {
                    override fun onSuccess(result: Unit) {
                        showAlert("SDK Unlock", "Biometric auth activated")
                        binding.unlockMethodsValue.text =
                            FuturaeSdkWrapper.client.getActiveUnlockMethods().joinToString()
                    }

                    override fun onError(throwable: Throwable) {
                        showErrorAlert("SDK Unlock", throwable)
                    }

                }
            )
        }
        binding.buttonDeactivateBiometrics.setOnClickListener {
            FuturaeSdkWrapper.client.deactivateBiometrics(object : Callback<Unit> {
                override fun onSuccess(result: Unit) {
                    showAlert("SDK Unlock", "Deactivated biometric authentication")
                    binding.unlockMethodsValue.text =
                        FuturaeSdkWrapper.client.getActiveUnlockMethods().joinToString()
                }

                override fun onError(throwable: Throwable) {
                    showErrorAlert("SDK Unlock", throwable)
                }
            })
        }
        binding.unlockMethodsValue.text = FuturaeSdkWrapper.client.getActiveUnlockMethods().joinToString()
        binding.buttonMigrationExecute.setOnClickListener {
            attemptRestoreAccounts()
        }
        binding.buttonUnlockMethods.setOnClickListener {
            showAlert(
                "SDK Unlock",
                "Active Unlock methods:\n" + FuturaeSdkWrapper.client.getActiveUnlockMethods().joinToString()
            )
            binding.unlockMethodsValue.text =
                FuturaeSdkWrapper.client.getActiveUnlockMethods().joinToString()
        }
        binding.buttonTotpOfflineBio.setOnClickListener {
            val accounts = FuturaeSdkWrapper.client.accounts
            if (accounts.isEmpty()) {
                showAlert("SDK Unlock", "No account enrolled")
            } else {
                val account = accounts[0]
                try {
                    FuturaeSdkWrapper.client.nextTotpWithBiometrics(
                        account.userId,
                        requireActivity(),
                        "Bio auth for TOTP",
                        "Authenticate with biometrics",
                        "Authenticate with biometrics to create a TOTP",
                        "cancel",
                        object : Callback<CurrentTotp> {
                            override fun onSuccess(result: CurrentTotp) {
                                showAlert(
                                    "TOTP",
                                    "Code: ${result.passcode}\nRemaining seconds: ${result.remainingSecs}"
                                )
                            }

                            override fun onError(throwable: Throwable) {
                                showAlert(
                                    "TOTP Error",
                                    "Biometric Auth failed with error: ${throwable.message}"
                                )
                            }

                        }
                    )
                } catch (e: LockOperationIsLockedException) {
                    showErrorAlert("SDK Unlock", e)
                }
            }
        }
        binding.buttonQRCodeWithBIO.setOnClickListener {
            currentRequest = REQUEST_QR_OFFLINE_WITH_BIO
            getQRCodeCallback.launch(
                FTRQRCodeActivity.getIntent(requireContext(), true, false),
            )
        }
        binding.buttonHotp.setOnClickListener {
            onHotpAuth()
        }
        binding.buttonAccStatus.setOnClickListener {
            getAccountsStatus()
        }
        binding.buttonAccHistory.setOnClickListener {
            getAccountHistory()
        }
    }

    override fun toggleAdaptiveButton(): MaterialButton = binding.buttonAdaptive

    override fun viewAdaptiveCollectionsButton(): MaterialButton = binding.buttonViewAdaptiveCollections

    override fun setAdaptiveThreshold(): MaterialButton = binding.buttonConfigureAdaptiveTime

    override fun serviceLogoButton(): MaterialButton = binding.buttonServiceLogo
}