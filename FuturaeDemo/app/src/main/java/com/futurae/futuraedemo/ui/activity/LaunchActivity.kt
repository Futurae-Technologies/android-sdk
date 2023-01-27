package com.futurae.futuraedemo.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.ActivityLauncherBinding
import com.futurae.futuraedemo.ui.App
import com.futurae.futuraedemo.ui.showAlert
import com.futurae.futuraedemo.ui.showDialog
import com.futurae.futuraedemo.ui.showErrorAlert
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.LockConfigurationType
import timber.log.Timber

class LaunchActivity : ComponentActivity() {

    private lateinit var binding: ActivityLauncherBinding

    companion object {
        const val DURATION_UNLOCK_SECONDS = 60
    }

    private var lockConfigurationType: LockConfigurationType? = null

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
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermissions()

        binding.buttonBiometricOrCreds.setOnClickListener {
            startHomeActivityWithConfig(LockConfigurationType.BIOMETRICS_OR_DEVICE_CREDENTIALS)
        }
        binding.buttonBiometricOrPin.setOnClickListener {
            startHomeActivityWithConfig(LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL)
        }
        binding.buttonBiometrics.setOnClickListener {
            startHomeActivityWithConfig(LockConfigurationType.BIOMETRICS_ONLY)
        }
        binding.buttonNone.setOnClickListener {
            startHomeActivityWithConfig(LockConfigurationType.NONE)
        }
        binding.buttonReset.setOnClickListener {
            FuturaeSDK.INSTANCE.reset(this)
        }
        checkForFTUri(intent)
    }

    private fun checkForFTUri(intent: Intent?) {
        intent?.dataString?.takeIf { it.isNotBlank() }?.let { uri ->
            lockConfigurationType?.let { type ->
                startActivity(HomeActivity.newIntent(this, type, uri))
            } ?: showErrorAlert("URI Alert", IllegalStateException("Unable to handle URI before initializing SDK with a lock configuration."))
        } ?: Timber.d("No URI found in provided Intent")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        checkForFTUri(intent)
    }

    private fun startHomeActivityWithConfig(type: LockConfigurationType, uri: String? = null) {
        if (lockConfigurationType != null && lockConfigurationType != type) {
            showErrorAlert(
                "App Error",
                IllegalStateException("SDK Already initialized with lock configuration type: ${lockConfigurationType}")
            )
            return
        }
        lockConfigurationType = type
        startHomeActivity(type, uri)
    }

    private fun startHomeActivity(type: LockConfigurationType, uri: String? = null) {
        try {
            (application as App).launchFuturaeSDKWithConfig(
                type,
                DURATION_UNLOCK_SECONDS
            )
            startActivity(
                HomeActivity.newIntent(this, type, uri)
            )
        } catch (e: IllegalStateException) {
            Timber.w("SDK is already initialized.", e)
            startActivity(
                HomeActivity.newIntent(this, type, uri)
            )
        } catch (e: Exception) {
            // SDK initialization error. Handle
            Timber.e(e)
            showDialog(
                "SDK Initialization",
                "Error:\n" + e.localizedMessage,
                "ok",
                {
                    Toast.makeText(this@LaunchActivity, "Reset SDK first", Toast.LENGTH_SHORT)
                        .show()
                }
            )
        }
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
                    getString(R.string.camera_perm_explain),
                    "ok",
                    {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    })
            }
        }

    }
}