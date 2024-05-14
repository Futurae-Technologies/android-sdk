package com.futurae.futuraedemo.ui.fragment

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.databinding.FragmentSdkSettingsBinding
import com.futurae.futuraedemo.ui.activity.ActivityPin
import com.futurae.futuraedemo.ui.activity.ActivitySDKConfiguration
import com.futurae.futuraedemo.ui.activity.EXTRA_CONFIG
import com.futurae.futuraedemo.ui.activity.EXTRA_PIN
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.Callback
import com.futurae.sdk.FuturaeCallback
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.LockConfigurationType
import com.futurae.sdk.SDKConfiguration
import com.futurae.sdk.debug.FuturaeDebugUtil
import com.futurae.sdk.exception.FTMissingTokensException
import com.futurae.sdk.exception.LockCorruptedStateException
import com.futurae.sdk.model.IntegrityResult
import timber.log.Timber
import java.lang.IllegalStateException


class FragmentSettings : Fragment() {

    lateinit var binding: FragmentSdkSettingsBinding

    private var listener: Listener? = null
    private lateinit var updatedConfiguration: SDKConfiguration

    private val switchConfigCallback = object : Callback<Unit> {
        override fun onSuccess(result: Unit) {
            listener?.onSDKConfigurationChanged(updatedConfiguration)
        }

        override fun onError(throwable: Throwable) {
            showErrorAlert("SDK Error", throwable)
        }
    }

    private val launchSDKConfiguration =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data?.hasExtra(EXTRA_CONFIG) == true) {
                val config = result.data?.getParcelableExtra<SDKConfiguration>(EXTRA_CONFIG) as SDKConfiguration
                switchConfiguration(config)
            }
        }

    private val launchPin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK
            && result.data?.hasExtra(EXTRA_PIN) == true
        ) {
            try {
                FuturaeSdkWrapper.sdk.switchToLockConfigurationWithPin(
                    requireActivity().application,
                    updatedConfiguration,
                    result.data?.getCharArrayExtra(EXTRA_PIN) as CharArray,
                    switchConfigCallback
                )
            } catch (e: Exception) {
                showErrorAlert("SDK Error", e)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSdkSettingsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLoggout.setOnClickListener {
            val accounts = FuturaeSdkWrapper.sdk.getClient().accounts
            if (accounts.isEmpty()) {
                showErrorAlert("SDK Error", Throwable("No accounts found to logout"))
            } else {
                accounts.forEach { account ->
                    FuturaeSdkWrapper.sdk.getClient().logout(account.userId, object : FuturaeCallback {
                        override fun success() {
                            Toast.makeText(requireContext(), "Logout successful", Toast.LENGTH_SHORT).show()
                        }

                        override fun failure(throwable: Throwable) {
                            showErrorAlert("Logout Error", throwable)
                        }
                    })
                }
            }
        }
        binding.buttonReset.setOnClickListener {
            FuturaeSdkWrapper.sdk.reset(requireContext())
            listener?.onSDKReset()
        }
        binding.buttonSwitchConfig.setOnClickListener {
            launchSDKConfiguration.launch(
                Intent(requireContext(), ActivitySDKConfiguration::class.java)
            )
        }
        binding.buttonIntegrityApi.setOnClickListener {
            FuturaeSdkWrapper.client.getIntegrityVerdict(object : Callback<IntegrityResult> {
                override fun onSuccess(result: IntegrityResult) {
                    showAlert(
                        "SDK Integrity Verdict",
                        "App Verdict: ${result.appVerdict}\n" +
                                "Device Verdict: ${result.deviceVerdicts.joinToString(separator = ",")}\n" +
                                "Account Verdict: ${result.licenseVerdict}"
                    )
                }

                override fun onError(throwable: Throwable) {
                    showErrorAlert("SDK Error", throwable)
                }

            })
        }
        binding.buttonClearEncrypted.setOnClickListener {
            FuturaeDebugUtil.clearEncryptedTokens()
        }
        binding.buttonClearV2Keys.setOnClickListener {
            FuturaeDebugUtil.corruptV2Keys(requireContext())
        }
        binding.buttonClearV2LocalStorageKey.setOnClickListener {
            FuturaeDebugUtil.corruptEncryptedStorageKey(requireContext())
        }
        binding.buttonCheckSigning.setOnClickListener {
            try {
                FuturaeSDK.validateSDKSigning()
                Toast.makeText(requireContext(),"SDK healthy", Toast.LENGTH_SHORT).show()
            } catch (e : Throwable) {
                when(e) {
                    is IllegalStateException -> showErrorAlert("SDK Uninitialized",e)
                    is LockCorruptedStateException -> showErrorAlert("SDK Corrupted",e)
                    is FTMissingTokensException -> showErrorAlert("SDK Encrypted Storage error",e)
                }
            }
        }
    }

    private fun switchConfiguration(config: SDKConfiguration) {
        try {
            updatedConfiguration = config
            when (config.lockConfigurationType) {
                LockConfigurationType.NONE -> {
                    FuturaeSdkWrapper.sdk.switchToLockConfigurationNone(
                        requireActivity().application,
                        config,
                        switchConfigCallback
                    )
                }

                LockConfigurationType.BIOMETRICS_ONLY -> {
                    showDialog(
                        "SDK",
                        "Authenticate to complete configuration change",
                        "OK",
                        {
                            FuturaeSdkWrapper.sdk.switchToLockConfigurationBiometrics(
                                config,
                                requireActivity(),
                                "Authenticate",
                                "Authentication is required to complete the switch",
                                "Authentication is required to complete the switch",
                                "Cancel",
                                switchConfigCallback
                            )
                        }
                    )
                }

                LockConfigurationType.BIOMETRICS_OR_DEVICE_CREDENTIALS -> {
                    showDialog(
                        "SDK",
                        "Authenticate to complete configuration change",
                        "OK",
                        {
                            FuturaeSdkWrapper.sdk.switchToLockConfigurationBiometricsOrCredentials(
                                config,
                                requireActivity(),
                                "Authenticate",
                                "Authentication is required to complete the switch",
                                "Authentication is required to complete the switch",
                                "Cancel",
                                switchConfigCallback
                            )
                        })
                }

                LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL -> {
                    updatedConfiguration = config
                    launchPin.launch(ActivityPin.newIntentForConfigChange(requireContext()))
                }
            }
        } catch (e: Exception) {
            showErrorAlert("SDK Error", e)
        }
    }

    interface Listener {
        fun onSDKReset()
        fun onSDKConfigurationChanged(sdkConfiguration: SDKConfiguration)
    }
}