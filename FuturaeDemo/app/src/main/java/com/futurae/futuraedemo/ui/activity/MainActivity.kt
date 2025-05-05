package com.futurae.futuraedemo.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.ActivityFragmentContainerBinding
import com.futurae.futuraedemo.ui.fragment.FragmentConfiguration
import com.futurae.futuraedemo.ui.fragment.FragmentMain
import com.futurae.futuraedemo.ui.fragment.FragmentPin
import com.futurae.futuraedemo.ui.fragment.FragmentSettings
import com.futurae.futuraedemo.ui.qr_push_action.QRCodeFlowOpenCoordinator
import com.futurae.futuraedemo.ui.qr_push_action.QRCodeRequestedActionHandler
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.Callback
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.common.LockConfigurationType.*
import com.futurae.sdk.public_api.common.SDKConfiguration
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForBiometricsPrompt
import com.futurae.sdk.public_api.common.model.PresentationConfigurationForDeviceCredentialsPrompt
import com.futurae.sdk.public_api.exception.FTApiPinIncorrectException
import com.futurae.sdk.public_api.exception.FTCorruptedStateException
import com.futurae.sdk.public_api.exception.FTInvalidStateException
import com.futurae.sdk.public_api.exception.FTKeyNotFoundException
import com.futurae.sdk.public_api.exception.FTKeystoreOperationException
import com.futurae.sdk.public_api.exception.FTLockInvalidConfigurationException
import com.futurae.sdk.public_api.exception.FTLockMechanismUnavailableException
import com.futurae.sdk.public_api.lock.model.WithBiometrics
import com.futurae.sdk.public_api.lock.model.WithBiometricsOrDeviceCredentials
import com.futurae.sdk.public_api.lock.model.WithSDKPin

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

        if (FuturaeSDK.isSDKInitialized) {
            onSDKLaunched(localStorage.getPersistedSDKConfig())
        } else {
            if (localStorage.hasExistingConfiguration()) {
                // show operations ui for configuration
                onConfigurationSelected(localStorage.getPersistedSDKConfig())
            } else {
                // show select configuration ui
                showSelectConfigurationUI()
            }
        }

        checkPermissions()

        // handling when activity is started via QR intent
        if (isQrCodeScannerLaunchIntent(intent)) {
            handleLaunchQRCodeIntent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // handling when activity was launched when intent came in
        if (isQrCodeScannerLaunchIntent(intent)) {
            handleLaunchQRCodeIntent()
        }
    }

    private fun isQrCodeScannerLaunchIntent(intent: Intent?): Boolean {
        return intent != null && intent.getBooleanExtra(
            QRCodeRequestedActionHandler.CLIENT_APP_QR_ACTION_BOOLEAN_EXTRA,
            false
        )
    }

    private fun handleLaunchQRCodeIntent() {
        QRCodeFlowOpenCoordinator.instance.notifyShouldOpenQRCode()
    }

    private fun showSelectConfigurationUI() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, FragmentConfiguration())
            .commit()
    }

    override fun onConfigurationSelected(sdkConfiguration: SDKConfiguration) {
        try {
            FuturaeSDK.launch(
                application,
                sdkConfiguration
            )
            onSDKLaunched(sdkConfiguration)
        } catch (e: Exception) {
            when (e) {
                is FTInvalidStateException -> showErrorAlert("SDK Already initialized", e)
                // Indicates that provided SDK configuration is invalid
                is FTLockInvalidConfigurationException -> showErrorAlert(
                    "SDK Configuration error",
                    e
                )
                // Indicates that provided SDK configuration is valid but cannot be supported on this device
                is FTLockMechanismUnavailableException -> showErrorAlert(
                    "SDK Configuration unsupported on device",
                    e
                )
                // Indicates that an SDK cryptographic operation failed
                is FTKeystoreOperationException -> showErrorAlert("SDK Cryptography Error", e)
                // Indicates that cryptographic keys are missing.
                is FTKeyNotFoundException -> showErrorAlert("SDK Missing Key", e)
                // Indicates that the SDK is in a corrupted state and should attempt to recover
                is FTCorruptedStateException -> {
                    if (sdkConfiguration.lockConfigurationType == SDK_PIN_WITH_BIOMETRICS_OPTIONAL) {
                        getPinWithCallback {
                            attemptSDKRecovery(sdkConfiguration, it)
                        }
                    } else {
                        attemptSDKRecovery(sdkConfiguration, null)
                    }
                }

                else -> showErrorAlert("SDK initialization failed", e)
            }
        }
    }

    private fun attemptSDKRecovery(sdkConfiguration: SDKConfiguration, sdkPin: CharArray?) {
        val userPresenceVerificationMode = when (sdkConfiguration.lockConfigurationType) {
            NONE -> null
            BIOMETRICS_ONLY -> WithBiometrics(
                PresentationConfigurationForBiometricsPrompt(
                    this,
                    "SDK Recovery",
                    "Authenticate to recover",
                    "Authenticate to recover",
                    "Cancel",
                )
            )

            BIOMETRICS_OR_DEVICE_CREDENTIALS -> WithBiometricsOrDeviceCredentials(
                PresentationConfigurationForDeviceCredentialsPrompt(
                    this,
                    "SDK Recovery",
                    "Authenticate to recover",
                    "Authenticate to recover",
                )
            )

            SDK_PIN_WITH_BIOMETRICS_OPTIONAL -> sdkPin?.let {
                WithSDKPin(it)
            }
                ?: throw IllegalStateException("Unable to recover without SDK PIN for provided SDKConfiguration")
        }
        FuturaeSDK.launchAccountRecovery(
            application,
            sdkConfiguration,
            userPresenceVerificationMode,
            object : Callback<Unit> {
                override fun onSuccess(result: Unit) {
                    onSDKLaunched(sdkConfiguration)
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is FTApiPinIncorrectException) {
                        showDialog(
                            title = "SDK Recovery failed",
                            message = throwable.localizedMessage ?: "",
                            positiveButton = "Re-attempt recovery",
                            positiveButtonCallback = {
                                // To get here should always be on SDK_PIN_WITH_BIOMETRICS_OPTIONAL
                                getPinWithCallback {
                                    attemptSDKRecovery(sdkConfiguration, it)
                                }
                            }
                        )
                    } else {
                        showErrorAlert("SDK Recovery failed", throwable)
                    }
                }
            }
        )
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

    private fun onSDKLaunched(sdkConfiguration: SDKConfiguration) {
        localStorage.persistSDKConfiguration(sdkConfiguration)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, FragmentMain())
            .commit()
        pendingUri?.let {
            handleUri(it)
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

    protected fun getPinWithCallback(callback: (CharArray) -> Unit) {
        val pinFragment = FragmentPin()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, pinFragment.apply {
                listener = object : FragmentPin.Listener {
                    override fun onPinComplete(pin: CharArray) {
                        parentFragmentManager.beginTransaction().remove(pinFragment).commit()
                        callback(pin)
                    }
                }
            })
            .commit()
    }
}
