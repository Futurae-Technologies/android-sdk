package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockBioCredsBinding
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForDeviceCredentialsPrompt
import com.futurae.sdk.public_api.lock.model.WithBiometricsOrDeviceCredentials
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class FragmentSDKUnlockBioCreds : FragmentSDKOperations() {

    private lateinit var binding: FragmentSdkUnlockBioCredsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSdkUnlockBioCredsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonUnlock.setOnClickListener {
            lifecycleScope.launch {
                try {
                    FuturaeSDK.client.lockApi.unlock(
                        WithBiometricsOrDeviceCredentials(
                            PresentationConfigurationForDeviceCredentialsPrompt(
                                requireActivity(),
                                "Unlock SDK",
                                "Authenticate with biometrics",
                                "Authentication is required to unlock SDK operations",
                            )
                        ),
                        shouldWaitForSDKSync = true
                    ).await()
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
        binding.buttonHotp.setOnClickListener {
            onHotpAuth()
        }
        binding.buttonAccHistory.setOnClickListener {
            getAccountHistory()
        }
        binding.buttonAccStatus.setOnClickListener {
            getAccountsStatus()
        }
        binding.unlockMethodsValue.text =
            FuturaeSDK.client.lockApi.getActiveUnlockMethods().joinToString()
    }

    override fun toggleAdaptiveButton(): MaterialButton = binding.buttonAdaptive

    override fun viewAdaptiveCollectionsButton(): MaterialButton =
        binding.buttonViewAdaptiveCollections

    override fun setAdaptiveThreshold(): MaterialButton = binding.buttonConfigureAdaptiveTime

    override fun serviceLogoButton(): MaterialButton = binding.buttonServiceLogo
    override fun timeLeftView(): TextView = binding.textTimerValue
    override fun sdkStatus(): TextView = binding.textStatusValue
}