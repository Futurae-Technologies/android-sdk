package com.futurae.futuraedemo.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.ActivityFragmentContainerBinding
import com.futurae.futuraedemo.ui.fragment.FragmentConfiguration
import com.futurae.futuraedemo.ui.fragment.FragmentMain
import com.futurae.futuraedemo.ui.fragment.FragmentSettings
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.SDKConfiguration


class MainActivity : FuturaeActivity(), FragmentConfiguration.Listener, FragmentSettings.Listener {

    lateinit var binding: ActivityFragmentContainerBinding

    private val localStorage: LocalStorage by lazy {
        LocalStorage(this)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            //Camera perm granted, can start camera activity
        } else {
            showAlert(
                "Camera Permission required",
                "Please enable camera permission from App settings, in order to scan barcodes"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFragmentContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (localStorage.hasExistingConfiguration()) {
            // show operations ui for configuration
            onConfigurationSelected(localStorage.getPersistedSDKConfig())
        } else {
            // show select configuration ui
            showSelectConfigurationUI()
        }
        checkPermissions()
    }

    private fun showSelectConfigurationUI() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, FragmentConfiguration())
            .commit()
    }

    override fun showLoading() {
        binding.progressLayout.isVisible = true
    }

    override fun hideLoading() {
        binding.progressLayout.isVisible = false
    }

    override fun onConfigurationSelected(sdkConfiguration: SDKConfiguration) {
        try {
            FuturaeSdkWrapper.sdk.launch(
                application,
                sdkConfiguration
            )
            localStorage.persistSDKConfiguration(sdkConfiguration)
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, FragmentMain())
                .commit()
        } catch (e: Exception) {
            showErrorAlert("SDK Error", e)
        }
    }

    override fun onSDKReset() {
        localStorage.reset()
        showSelectConfigurationUI()
    }

    override fun onSDKConfigurationChanged(sdkConfiguration: SDKConfiguration) {
        localStorage.persistSDKConfiguration(sdkConfiguration)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, FragmentMain())
            .commit()
    }

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                //All good
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showDialog(
                    "Camera Permission required",
                    "Please enable camera permission from App settings, in order to scan barcodes",
                    "Open Settings",
                    {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        })
                    }
                )
            }

            else -> {
                showDialog(
                    "Permission Request",
                    "In order to scan a QR code, Futurae requires access to the camera.",
                    "ok",
                    {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    })
            }
        }

    }

}