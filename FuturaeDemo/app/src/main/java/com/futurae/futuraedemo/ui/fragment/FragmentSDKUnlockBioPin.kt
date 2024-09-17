package com.futurae.futuraedemo.ui.fragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockBioPinBinding
import com.futurae.futuraedemo.ui.activity.FTRQRCodeActivity
import com.futurae.futuraedemo.util.getParcelable
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.showInputDialog
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.account.model.ActivationCode
import com.futurae.sdk.public_api.account.model.EnrollAccountAndSetupSDKPin
import com.futurae.sdk.public_api.account.model.EnrollmentParams
import com.futurae.sdk.public_api.auth.model.Biometrics
import com.futurae.sdk.public_api.auth.model.PinCode
import com.futurae.sdk.public_api.auth.model.SDKAuthMode
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForBiometricsPrompt
import com.futurae.sdk.public_api.exception.FTMalformedQRCodeException
import com.futurae.sdk.public_api.exception.FTUnlockRequiredException
import com.futurae.sdk.public_api.lock.model.WithBiometrics
import com.futurae.sdk.public_api.lock.model.WithSDKPin
import com.futurae.sdk.public_api.session.model.ApproveInfo
import com.futurae.sdk.utils.FTQRCodeUtils
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This is the most complex UI code, because it doesn't use FragmentSDKOperations for scanning QR codes.
 * This Fragment has to manage the state of scanned QR codes, provided PIN and current SDK operation flow.
 */
class FragmentSDKUnlockBioPin : FragmentSDKOperations() {

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
                result.data?.getParcelable(
                    FTRQRCodeActivity.PARAM_BARCODE,
                    Barcode::class.java
                )?.let { qrcode ->
                    when (currentRequest) {
                        REQUEST_ENROLL_WITH_PIN -> {
                            if (FTQRCodeUtils.getQrcodeType(qrcode.rawValue) == FTQRCodeUtils.QRType.Enroll) {
                                getPinWithCallback {
                                    requireContext().showInputDialog("Flow Binding Token") { token ->
                                        lifecycleScope.launch {
                                            try {
                                                FuturaeSDK.client.accountApi.enrollAccount(
                                                    EnrollmentParams(
                                                        inputCode = ActivationCode(qrcode.rawValue),
                                                        enrollmentUseCase = EnrollAccountAndSetupSDKPin(
                                                            it
                                                        ),
                                                        token
                                                    )
                                                ).await()
                                            } catch (t: Throwable) {
                                                showErrorAlert("Enroll API Erro", t)
                                            } finally {
                                                currentRequest = 0
                                            }
                                        }
                                    }
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
                            if (FTQRCodeUtils.getQrcodeType(qrcode.rawValue) == FTQRCodeUtils.QRType.Offline) {
                                val accounts = FuturaeSDK.client.accountApi.getActiveAccounts()
                                if (accounts.isEmpty()) {
                                    showAlert("SDK Unlock", "No account enrolled")
                                    currentRequest = 0
                                } else {
                                    getPinWithCallback {
                                        try {
                                            val extras: List<ApproveInfo>? = try {
                                                FuturaeSDK.client.sessionApi.extractQRCodeExtraInfo(
                                                    qrcode.rawValue
                                                )
                                            } catch (e: FTMalformedQRCodeException) {
                                                Timber.e(e)
                                                showErrorAlert("SDK Unlock", e)
                                                currentRequest = 0
                                                null
                                            }
                                            val sb = StringBuffer()
                                            if (extras != null) {
                                                sb.append("\n")
                                                for (info in extras) {
                                                    sb.append(info.key).append(": ")
                                                        .append(info.value)
                                                        .append("\n")
                                                }
                                            }
                                            showDialog("Approve", "Request Info: $sb", "Approve", {
                                                lifecycleScope.launch {
                                                    val verificationSignature = try {
                                                        FuturaeSDK.client.authApi.getOfflineQRVerificationCode(
                                                            qrcode.rawValue,
                                                            SDKAuthMode.PinOrBiometrics(
                                                                PinCode(it)
                                                            )
                                                        ).await()
                                                    } catch (e: Exception) {
                                                        Timber.e(e)
                                                        showErrorAlert("SDK Unlock", e)
                                                    }
                                                    currentRequest = 0
                                                    showAlert(
                                                        "Confirm Transaction",
                                                        "To Approve the transaction, enter: $verificationSignature in the browser"
                                                    )
                                                }
                                            }, " Deny", {
                                                //Nothing
                                            })
                                        } catch (e: FTUnlockRequiredException) {
                                            Timber.e(e)
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
                            if (FTQRCodeUtils.getQrcodeType(qrcode.rawValue) == FTQRCodeUtils.QRType.Offline) {
                                val extras: List<ApproveInfo>? = try {
                                    FuturaeSDK.client.sessionApi.extractQRCodeExtraInfo(qrcode.rawValue)
                                } catch (e: FTMalformedQRCodeException) {
                                    Timber.e(e)
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
                                        lifecycleScope.launch {
                                            try {
                                                val code =
                                                    FuturaeSDK.client.authApi.getOfflineQRVerificationCode(
                                                        qrcode.rawValue,
                                                        SDKAuthMode.PinOrBiometrics(
                                                            Biometrics(
                                                                PresentationConfigurationForBiometricsPrompt(
                                                                    requireActivity(),
                                                                    "Bio auth",
                                                                    "Authenticate",
                                                                    "Authenticate to unlock PIN",
                                                                    "cancel",
                                                                )
                                                            )
                                                        )
                                                    ).await()
                                                showAlert(
                                                    "Confirm Transaction",
                                                    "To Approve the transaction, enter: $code in the browser"
                                                )
                                            } catch (t: Throwable) {
                                                showErrorAlert("Auth API error", t)
                                            }
                                        }
                                    })
                            } else {
                                showErrorAlert(
                                    "SDK Unlock",
                                    IllegalStateException("Invalid QR code for Offline Auth")
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
            lifecycleScope.launch {
                try {
                    FuturaeSDK.client.lockApi.unlock(
                        userPresenceVerificationMode = WithBiometrics(
                            PresentationConfigurationForBiometricsPrompt(
                                requireActivity(),
                                "Unlock SDK",
                                "Authenticate with biometrics",
                                "Authentication is required to unlock SDK operations",
                                "cancel",
                            )
                        ),
                        shouldWaitForSDKSync = true
                    ).await()
                    if (FuturaeSDK.client.adaptiveApi.isAdaptiveEnabled()) {
                        FuturaeSDK.client.adaptiveApi.collectAndSubmitObservations()
                    }
                } catch (t: Throwable) {
                    showErrorAlert("Lock API Error", t)
                }
            }
        }
        binding.buttonLock.setOnClickListener {
            FuturaeSDK.client.lockApi.lock()
        }
        binding.buttonChangePin.setOnClickListener {
            getPinWithCallback {
                lifecycleScope.launch {
                    try {
                        FuturaeSDK.client.lockApi.changeSDKPin(it).await()
                    } catch (t: Throwable) {
                        showErrorAlert("Lock API Error", t)
                    } finally {
                        currentRequest = 0
                    }
                }
            }
        }
        binding.buttonUnlockWithPin.setOnClickListener {
            getPinWithCallback {
                lifecycleScope.launch {
                    try {
                        FuturaeSDK.client.lockApi.unlock(
                            userPresenceVerificationMode = WithSDKPin(it),
                            shouldWaitForSDKSync = true
                        ).await()
                        if (FuturaeSDK.client.adaptiveApi.isAdaptiveEnabled()) {
                            FuturaeSDK.client.adaptiveApi.collectAndSubmitObservations()
                        }
                    } catch (t: Throwable) {
                        showErrorAlert("Lock API Error", t)
                    }
                }
            }
        }
        binding.buttonEnroll.setOnClickListener {
            scanQRCode()
        }
        binding.buttonEnrollActivation.setOnClickListener {
            if(FuturaeSDK.client.accountApi.getActiveAccounts().isEmpty()) {
                getPinWithCallback {
                    onActivationCodeEnroll(it)
                }
            } else {
                onActivationCodeEnroll()
            }
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
                val accounts = FuturaeSDK.client.accountApi.getActiveAccounts()
                if (accounts.isEmpty()) {
                    showAlert("SDK Unlock", "No account enrolled")
                } else {
                    lifecycleScope.launch {
                        val account = accounts[0]
                        try {
                            val totp = FuturaeSDK.client.authApi.getTOTP(
                                account.userId,
                                SDKAuthMode.PinOrBiometrics(
                                    PinCode(it)
                                )
                            ).await()
                            showAlert(
                                "TOTP",
                                "Code: ${totp.passcode}\nRemaining seconds: ${totp.remainingSeconds}"
                            )
                        } catch (e: FTUnlockRequiredException) {
                            showErrorAlert("SDK Unlock", e)
                        }
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
            lifecycleScope.launch {
                try {
                    FuturaeSDK.client.lockApi.activateBiometrics(
                        PresentationConfigurationForBiometricsPrompt(
                            requireActivity(),
                            "Unlock SDK",
                            "Activate biometrics",
                            "Authenticate to enable biometric authentication unlocking",
                            "cancel",
                        )
                    ).await()
                    binding.unlockMethodsValue.text =
                        FuturaeSDK.client.lockApi.getActiveUnlockMethods().joinToString()
                } catch (t: Throwable) {
                    showErrorAlert("Lock API Error", t)
                }
            }
        }
        binding.buttonDeactivateBiometrics.setOnClickListener {
            lifecycleScope.launch {
                try {
                    FuturaeSDK.client.lockApi.deactivateBiometrics().await()
                    binding.unlockMethodsValue.text =
                        FuturaeSDK.client.lockApi.getActiveUnlockMethods().joinToString()
                } catch (t: Throwable) {
                    showErrorAlert("Lock API Error", t)
                }
            }
        }
        binding.unlockMethodsValue.text =
            FuturaeSDK.client.lockApi.getActiveUnlockMethods().joinToString()
        binding.buttonMigrationExecute.setOnClickListener {
            attemptRestoreAccounts()
        }
        binding.buttonUnlockMethods.setOnClickListener {
            showAlert(
                "SDK Unlock",
                "Active Unlock methods:\n" + FuturaeSDK.client.lockApi.getActiveUnlockMethods()
                    .joinToString()
            )
            binding.unlockMethodsValue.text =
                FuturaeSDK.client.lockApi.getActiveUnlockMethods().joinToString()
        }
        binding.buttonTotpOfflineBio.setOnClickListener {
            val accounts = FuturaeSDK.client.accountApi.getActiveAccounts()
            if (accounts.isEmpty()) {
                showAlert("SDK Unlock", "No account enrolled")
            } else {
                val account = accounts[0]
                lifecycleScope.launch {
                    try {
                        val totp = FuturaeSDK.client.authApi.getTOTP(
                            account.userId,
                            SDKAuthMode.PinOrBiometrics(
                                Biometrics(
                                    PresentationConfigurationForBiometricsPrompt(
                                        requireActivity(),
                                        "Bio auth for TOTP",
                                        "Authenticate with biometrics",
                                        "Authenticate with biometrics to create a TOTP",
                                        "cancel",
                                    )
                                )
                            )
                        ).await()
                        showAlert(
                            "TOTP",
                            "Code: ${totp.passcode}\nRemaining seconds: ${totp.remainingSeconds}"
                        )
                    } catch (t: Throwable) {
                        showErrorAlert("Auth API error", t)
                    }
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
        binding.buttonCheckBioInvalidation.setOnClickListener {
            try {
                val haveBiometricsInvalidated = FuturaeSDK.client.lockApi.haveBiometricsChanged()
                Toast.makeText(
                    requireContext(),
                    "Invalidated: ${haveBiometricsInvalidated}",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (t: Throwable) {
                showErrorAlert("Lock API Error", t)
            }
        }
    }

    override fun serviceLogoButton(): MaterialButton = binding.buttonServiceLogo
    override fun timeLeftView(): TextView = binding.textTimerValue
    override fun sdkStatus(): TextView = binding.textStatusValue
    override fun accountInfoButton(): View = binding.buttonAccountInfo
}