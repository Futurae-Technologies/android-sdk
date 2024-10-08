package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockNoneBinding
import com.google.android.material.button.MaterialButton

class FragmentSDKUnlockNone : FragmentSDKOperations() {

    private lateinit var binding: FragmentSdkUnlockNoneBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSdkUnlockNoneBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        binding.buttonHotp.setOnClickListener {
            onHotpAuth()
        }
        binding.buttonAccStatus.setOnClickListener {
            getAccountsStatus()
        }
    }

    override fun serviceLogoButton(): MaterialButton = binding.buttonServiceLogo
    override fun timeLeftView(): TextView = binding.textTimerValue
    override fun sdkStatus(): TextView = binding.textStatusValue
    override fun accountInfoButton(): View = binding.buttonAccountInfo
}