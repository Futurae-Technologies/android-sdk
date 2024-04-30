package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.FragmentSdkMainBinding
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.sdk.public_api.common.LockConfigurationType

class FragmentMain : Fragment() {

    lateinit var binding: FragmentSdkMainBinding

    private val localStorage: LocalStorage by lazy {
        LocalStorage(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSdkMainBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.fragment_home -> showHomeFragment()
                R.id.fragment_settings -> showSettingsFragment()
            }
            return@setOnItemSelectedListener true
        }
        binding.bottomNavigationView.selectedItemId = R.id.fragment_home
    }

    private fun showHomeFragment() {
        val config = localStorage.getPersistedSDKConfig()

        val fragment = when (config.lockConfigurationType) {
            LockConfigurationType.NONE -> FragmentSDKUnlockNone()
            LockConfigurationType.BIOMETRICS_ONLY -> FragmentSDKUnlockBio()
            LockConfigurationType.BIOMETRICS_OR_DEVICE_CREDENTIALS -> FragmentSDKUnlockBioCreds()
            LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL -> FragmentSDKUnlockBioPin()
        }
        childFragmentManager
            .beginTransaction()
            .replace(R.id.childFragmentContainer, fragment)
            .commit()
    }

    private fun showSettingsFragment() {
        childFragmentManager
            .beginTransaction()
            .replace(R.id.childFragmentContainer, FragmentSettings())
            .commit()
    }
}