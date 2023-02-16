package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.futurae.futuraedemo.databinding.FragmentSdkUnlockNoneBinding

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
        binding.buttonQRCode.setOnClickListener {
            scanQRCode()
        }
        binding.buttonLogout.setOnClickListener {
            onLogout()
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
        binding.buttonAccHistory.setOnClickListener {
            getAccountHistory()
        }
        binding.buttonSyncAuthentication.setOnClickListener {
            onSyncAuthToken()
        }
        binding.buttonAccStatus.setOnClickListener {
            getAccountsStatus()
        }
    }
}