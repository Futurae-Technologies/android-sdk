package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockBioBinding
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForBiometricsPrompt
import com.futurae.sdk.public_api.lock.model.WithBiometrics
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class FragmentSDKUnlockBio : FragmentSDKOperations() {

    private lateinit var binding: FragmentSdkUnlockBioBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSdkUnlockBioBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun timeLeftView(): TextView = binding.textTimerValue

    override fun sdkStatus(): TextView = binding.textStatusValue
    override fun accountInfoButton(): View = binding.buttonAccountInfo

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonUnlock.setOnClickListener {
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
                    showErrorAlert("SDK Unlock", t)
                }
            }
        }
        binding.buttonLock.setOnClickListener {
            FuturaeSDK.client.lockApi.lock()
        }
        binding.buttonEnroll.setOnClickListener {
            scanQRCode()
        }
        binding.buttonEnrollActivation.setOnClickListener {
            onActivationCodeEnroll()
        }
        binding.buttonEnrollManual.setOnClickListener {
            onManualEntryEnroll()
        }
        binding.buttonQRCode.setOnClickListener {
            scanQRCode()
        }
        binding.buttonTotp.setOnClickListener {
            onTOTPAuth()
        }
        binding.buttonMigrationExecute.setOnClickListener {
            attemptRestoreAccounts()
        }
        binding.buttonAccHistory.setOnClickListener {
            getAccountHistory()
        }
        binding.buttonAccStatus.setOnClickListener {
            getAccountsStatus()
        }
        binding.buttonHotp.setOnClickListener {
            onHotpAuth()
        }
        binding.unlockMethodsValue.text =
            FuturaeSDK.client.lockApi.getActiveUnlockMethods().joinToString()
    }

    override fun serviceLogoButton(): MaterialButton = binding.buttonServiceLogo
}