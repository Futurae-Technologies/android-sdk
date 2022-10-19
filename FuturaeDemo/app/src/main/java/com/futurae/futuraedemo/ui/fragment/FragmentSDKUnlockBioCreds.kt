package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockBioCredsBinding
import com.futurae.futuraedemo.ui.showErrorAlert
import com.futurae.sdk.Callback
import com.futurae.sdk.FuturaeSDK
import timber.log.Timber

class FragmentSDKUnlockBioCreds : FragmentSDKLockedFragment() {

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
            FuturaeSDK.getClient().unlockWithBiometricsDeviceCredentials(
                requireActivity(),
                "Unlock SDK",
                "Authenticate with biometrics or device credentials",
                "Authentication is required to unlock SDK operations",
                object : Callback<Unit> {
                    override fun onSuccess(result: Unit) {
                        binding.textStatusValue.text = "Unlocked"
                        onUnlocked(binding.textTimerValue, binding.textStatusValue)
                    }

                    override fun onError(throwable: Throwable) {
                        onLocked(binding.textTimerValue, binding.textStatusValue)
                        showErrorAlert("SDK Unlock", throwable)
                    }
                }
            )
        }
        binding.buttonLock.setOnClickListener {
            FuturaeSDK.getClient().lock()
            onLocked(binding.textTimerValue, binding.textStatusValue)
        }
        binding.buttonEnroll.setOnClickListener {
            scanQRCode()
        }
        binding.buttonLogout.setOnClickListener {
            onLogout()
        }
        binding.buttonQRCode.setOnClickListener {
            scanQRCode()
        }
        binding.buttonTotp.setOnClickListener {
            onTOTPAuth()
        }
        binding.buttonMigrationCheck.setOnClickListener {
            onAccountsMigrationCheck()
        }
        binding.buttonMigrationExecute.setOnClickListener {
            onAccountsMigrationExecute()
        }
        binding.unlockMethodsValue.text = FuturaeSDK.getClient().activeUnlockMethods.joinToString()
    }
}