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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.futurae.futuraedemo.databinding.FragmentSdkSettingsBinding
import com.futurae.futuraedemo.ui.activity.ActivitySDKConfiguration
import com.futurae.futuraedemo.ui.activity.EXTRA_CONFIG
import com.futurae.futuraedemo.ui.activity.adaptive.AdaptiveOverviewActivity
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.futuraedemo.util.getParcelable
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.adaptive.debug.AdaptiveDebugUtil
import com.futurae.sdk.debug.FuturaeDebugUtil
import com.futurae.sdk.public_api.auth.model.ApproveParameters
import com.futurae.sdk.public_api.auth.model.SessionId
import com.futurae.sdk.public_api.common.LockConfigurationType
import com.futurae.sdk.public_api.common.SDKConfiguration
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForBiometricsPrompt
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForDeviceCredentialsPrompt
import com.futurae.sdk.public_api.exception.FTCorruptedStateException
import com.futurae.sdk.public_api.exception.FTEncryptedStorageCorruptedException
import com.futurae.sdk.public_api.exception.FTInvalidStateException
import com.futurae.sdk.public_api.lock.model.SwitchTargetLockConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber


class FragmentSettings : BaseFragment() {

    lateinit var binding: FragmentSdkSettingsBinding

    private var listener: Listener? = null
    private lateinit var updatedConfiguration: SDKConfiguration
    private lateinit var localStorage: LocalStorage

    private val launchSDKConfiguration =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data?.hasExtra(EXTRA_CONFIG) == true) {
                result.data?.getParcelable(EXTRA_CONFIG, SDKConfiguration::class.java)
                    ?.let { config ->
                        if (config.lockConfigurationType == LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL) {
                            getPinWithCallback {
                                switchConfiguration(config, it)
                            }
                        } else {
                            switchConfiguration(config, null)
                        }
                    }
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as Listener
        localStorage = LocalStorage(context)
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSdkSettingsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLoggout.setOnClickListener {
            val accounts = FuturaeSDK.client.accountApi.getActiveAccounts()
            if (accounts.isEmpty()) {
                showErrorAlert("SDK Error", Throwable("No accounts found to logout"))
            } else {
                lifecycleScope.launch {
                    try {
                        accounts.forEach { account ->
                            FuturaeSDK.client.accountApi.logoutAccount(account.userId).await()
                        }
                    } catch (t: Throwable) {
                        showErrorAlert("Logout Error", t)
                    }
                }
            }
        }
        binding.buttonReset.setOnClickListener {
            FuturaeSDK.reset(requireContext())
            listener?.onSDKReset()
        }
        binding.buttonSwitchConfig.setOnClickListener {
            launchSDKConfiguration.launch(
                Intent(requireContext(), ActivitySDKConfiguration::class.java)
            )
        }
        binding.buttonIntegrityApi.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = FuturaeSDK.client.operationsApi.getIntegrityVerdict().await()
                    showAlert(
                        "SDK Integrity Verdict",
                        "App Verdict: ${result.appVerdict}\n" +
                                "Device Verdict: ${result.deviceVerdicts.joinToString(separator = ",")}\n" +
                                "Account Verdict: ${result.licenseVerdict}"
                    )
                } catch (t: Throwable) {
                    showErrorAlert("Integrity API Error", t)
                }
            }
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
        binding.buttonCorruptAdaptiveLocalStorage.setOnClickListener {
            AdaptiveDebugUtil.corruptDB()
        }
        binding.buttonClearAdaptiveLocalStorageKey.setOnClickListener {
            AdaptiveDebugUtil.corruptEncryptedStorageKey()
        }
        binding.buttonTestWithDelay.setOnClickListener {
            lifecycleScope.launch {
                delay(10000)
                try {
                    val userIds = FuturaeSDK.client.accountApi.getActiveAccounts().map { it.userId }
                    val result =
                        FuturaeSDK.client.accountApi.getAccountsStatus(*userIds.toTypedArray())
                            .await()

                    result.statuses.firstOrNull { it.activeSessions.isNotEmpty() }?.let {
                        val sessionInfo = it.activeSessions.first()

                        val transactionBuilder = ApproveParameters.Builder(
                            // This session came from a user's accountsStatus. So it WILL have a user id.
                            SessionId(sessionInfo.userId!!, sessionInfo.sessionId)
                        )
                        sessionInfo.approveInfo?.let {
                            transactionBuilder.setExtraInfo(it)
                        }

                        FuturaeSDK.client.authApi.approve(
                            transactionBuilder.build()
                        ).await()
                    }
                } catch (t: Throwable) {
                    showErrorAlert("SDK error", t)
                }
            }
        }
        binding.buttonCheckSigning.setOnClickListener {
            try {
                FuturaeSDK.client.operationsApi.validateSDKSigning()
                Toast.makeText(requireContext(), "SDK healthy", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                when (e) {
                    is FTEncryptedStorageCorruptedException -> showErrorAlert(
                        "SDK Encrypted Storage error",
                        e
                    )

                    is FTInvalidStateException -> showErrorAlert("SDK Uninitialized", e)
                    is FTCorruptedStateException -> showErrorAlert("SDK Corrupted", e)
                }
            }
        }
        binding.buttonCheckPubUploaded.setOnClickListener {
            val keyUploaded = FuturaeSDK.client.operationsApi.isPublicKeyUploaded()
            Toast.makeText(requireContext(), "PK uploaded: ${keyUploaded}", Toast.LENGTH_SHORT)
                .show()
        }
        binding.buttonUploadPK.setOnClickListener {
            lifecycleScope.launch {
                try {
                    FuturaeSDK.client.operationsApi.uploadPublicKey().await()
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
        binding.buttonAdaptiveOverview.setOnClickListener {
            startActivity(Intent(requireContext(), AdaptiveOverviewActivity::class.java))
        }

        binding.buttonToggleAllowChangePinWithBio.isVisible = localStorage.getPersistedSDKConfig().lockConfigurationType == LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL
        binding.buttonToggleAllowChangePinWithBio.isChecked = localStorage.getPersistedSDKConfig().allowChangePinCodeWithBiometricUnlock
        binding.buttonToggleAllowChangePinWithBio.setOnCheckedChangeListener { buttonView, isChecked ->
            val sdkConfig = localStorage.getPersistedSDKConfig()
            val updatedConfig = sdkConfig.copy(allowChangePinCodeWithBiometricUnlock = isChecked)
            localStorage.persistSDKConfiguration(updatedConfig)
            val message = if(updatedConfig.allowChangePinCodeWithBiometricUnlock) {
                "changeSDKPin with Bio allowed for next SDK launch"
            } else {
                "changeSDKPin with Bio disabled for next SDK launch"
            }
            showAlert("SDK Config update", message)
        }

        binding.buttonToggleAllowUnprotectedSessionInfo.isChecked = localStorage.isUnprotectedSessionInfoEnabled
        binding.buttonToggleAllowUnprotectedSessionInfo.setOnCheckedChangeListener { _, isChecked ->
            localStorage.setUnprotectedSessionInfoEnabled(isChecked)
        }
    }

    private fun switchConfiguration(config: SDKConfiguration, sdkPin: CharArray? = null) {
        lifecycleScope.launch {
            try {
                updatedConfiguration = config
                val switchTarget = when (config.lockConfigurationType) {
                    LockConfigurationType.NONE -> SwitchTargetLockConfiguration.None(
                        updatedConfiguration
                    )

                    LockConfigurationType.BIOMETRICS_ONLY -> SwitchTargetLockConfiguration.Biometrics(
                        updatedConfiguration,
                        PresentationConfigurationForBiometricsPrompt(
                            requireActivity(),
                            "Authenticate",
                            "Authentication is required to complete the switch",
                            "Authentication is required to complete the switch",
                            "Cancel",
                        )
                    )

                    LockConfigurationType.BIOMETRICS_OR_DEVICE_CREDENTIALS -> SwitchTargetLockConfiguration.BiometricsOrCredentials(
                        updatedConfiguration,
                        PresentationConfigurationForDeviceCredentialsPrompt(
                            requireActivity(),
                            "Authenticate",
                            "Authentication is required to complete the switch",
                            "Authentication is required to complete the switch",
                        )
                    )

                    LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL -> SwitchTargetLockConfiguration.PinWithBiometricsOptional(
                        updatedConfiguration,
                        sdkPin
                            ?: throw IllegalStateException("Cannot switch to this configuration without an SDK PIN"),
                    )
                }
                FuturaeSDK.client.lockApi.switchToLockConfiguration(
                    requireActivity().application,
                    switchTarget
                ).await()
                listener?.onSDKConfigurationChanged(updatedConfiguration)
            } catch (e: Exception) {
                showErrorAlert("SDK Error", e)
            }
        }
    }

    interface Listener {
        fun onSDKReset()
        fun onSDKConfigurationChanged(sdkConfiguration: SDKConfiguration)
    }
}