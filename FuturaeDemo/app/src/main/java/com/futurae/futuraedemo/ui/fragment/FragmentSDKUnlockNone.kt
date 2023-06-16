package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun toggleAdaptiveButton(): MaterialButton = binding.buttonAdaptive
    override fun viewAdaptiveCollectionsButton(): MaterialButton = binding.buttonViewAdaptiveCollections
    override fun setAdaptiveThreshold(): MaterialButton = binding.buttonConfigureAdaptiveTime
    override fun serviceLogoButton(): MaterialButton = binding.buttonServiceLogo
}