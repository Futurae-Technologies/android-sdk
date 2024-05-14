package com.futurae.futuraedemo.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.FragmentSdkConfigurationBinding
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.LockConfigurationType
import com.futurae.sdk.SDKConfiguration
import com.futurae.sdk.debug.FuturaeDebugUtil

class FragmentConfiguration : Fragment() {

    lateinit var binding: FragmentSdkConfigurationBinding
    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSdkConfigurationBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.finishButton.setOnClickListener {

            val sdkLockConfiguration = when (binding.radioGroup.checkedRadioButtonId) {
                R.id.radioButtonNone -> LockConfigurationType.NONE
                R.id.radioButtonBiometrics -> LockConfigurationType.BIOMETRICS_ONLY
                R.id.radioButtonBiometricsOrCreds -> LockConfigurationType.BIOMETRICS_OR_DEVICE_CREDENTIALS
                R.id.radioButtonPin -> LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL
                else -> throw IllegalStateException("Unrecognized view ID: ${binding.radioGroup.checkedRadioButtonId}")
            }

            try {
                val duration = when (binding.chipGroup.checkedChipId) {
                    R.id.chip30 -> 30
                    R.id.chip60 -> 60
                    R.id.chip120 -> 120
                    R.id.chip180 -> 180
                    R.id.chip240 -> 240
                    R.id.chip300 -> 300
                    else -> throw IllegalStateException("Unrecognized duration selection")
                }
                val sdkConfig = SDKConfiguration.Builder()
                        .setUnlockDuration(duration)
                        .setLockConfigurationType(sdkLockConfiguration)
                        .setInvalidatedByBiometricChange(binding.checkboxBioInvalidation.isChecked)
                        .setUnlockedDeviceRequired(binding.checkboxDeviceUnlocked.isChecked)
                        .setSkipHardwareSecurity(binding.checkboxSkipHardwareStorage.isChecked)
                        .build()
                listener?.onConfigurationSelected(sdkConfig)
            } catch (e: Exception) {
                showErrorAlert("SDK Error", e)
            }
        }
        binding.corruptv1KeysButton.setOnClickListener {
            FuturaeDebugUtil.corruptV1Keys(requireContext())
        }
        binding.corruptv2KeysButton.setOnClickListener {
            FuturaeDebugUtil.corruptV2Keys(requireContext())
        }
        binding.corruptDbButton.setOnClickListener {
            FuturaeDebugUtil.corruptDBTokens(requireContext())
        }
    }

    interface Listener {
        fun onConfigurationSelected(sdkConfiguration: SDKConfiguration)
    }
}