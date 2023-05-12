package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockBioBinding
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.Callback
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.model.UserPresenceVerification
import com.google.android.material.button.MaterialButton
import timber.log.Timber

class FragmentSDKUnlockBio : FragmentSDKLockedFragment() {

    private lateinit var binding: FragmentSdkUnlockBioBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSdkUnlockBioBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonUnlock.setOnClickListener {
            FuturaeSDK.INSTANCE.client.unlockWithBiometrics(
                requireActivity(),
                "Unlock SDK",
                "Authenticate with biometrics",
                "Authentication is required to unlock SDK operations",
                "cancel",
                object : Callback<UserPresenceVerification> {
                    override fun onSuccess(result: UserPresenceVerification) {
                        binding.textStatusValue.text = "Unlocked"
                        onUnlocked(binding.textTimerValue, binding.textStatusValue)
                    }

                    override fun onError(throwable: Throwable) {
                        Timber.e(throwable)
                        onLocked(binding.textTimerValue, binding.textStatusValue)
                        showErrorAlert("SDK Unlock", throwable)
                    }

                }
            )
        }
        binding.buttonLock.setOnClickListener {
            FuturaeSDK.INSTANCE.client.lock()
            onLocked(binding.textTimerValue, binding.textStatusValue)
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
        binding.buttonAccHistory.setOnClickListener {
            getAccountHistory()
        }
        binding.buttonAccStatus.setOnClickListener {
            getAccountsStatus()
        }
        binding.buttonHotp.setOnClickListener {
            onHotpAuth()
        }
        binding.buttonAccStatus.setOnClickListener {
            getAccountsStatus()
        }
        binding.unlockMethodsValue.text = FuturaeSDK.INSTANCE.client.activeUnlockMethods.joinToString()
    }

    override fun serviceLogoButton(): MaterialButton = binding.buttonServiceLogo
}