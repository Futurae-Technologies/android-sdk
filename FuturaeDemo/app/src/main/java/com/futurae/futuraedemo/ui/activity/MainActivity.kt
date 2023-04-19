package com.futurae.futuraedemo.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.ActivityFragmentContainerBinding
import com.futurae.futuraedemo.ui.App
import com.futurae.futuraedemo.ui.EXTRA_APPROVE_SESSION
import com.futurae.futuraedemo.ui.fragment.FragmentConfiguration
import com.futurae.futuraedemo.ui.fragment.FragmentMain
import com.futurae.futuraedemo.ui.fragment.FragmentSettings
import com.futurae.futuraedemo.util.LocalStorage
import com.futurae.futuraedemo.util.showAlert
import com.futurae.futuraedemo.util.showDialog
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.futuraedemo.util.toDialogMessage
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.SDKConfiguration
import com.futurae.sdk.approve.ApproveSession
import timber.log.Timber


class MainActivity : FuturaeActivity(), FragmentConfiguration.Listener, FragmentSettings.Listener {

    lateinit var binding: ActivityFragmentContainerBinding

    private val localStorage: LocalStorage by lazy {
        LocalStorage(this)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!results.containsValue(false)) {
            //permissions granted
        } else {
            showAlert(
                "Permissions required",
                "Please enable camera and notification permissions from App settings, in order to scan barcodes and receive notifications"
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


        (intent.getParcelableExtra(EXTRA_APPROVE_SESSION) as? ApproveSession)?.let {
            onApproveAuth(it,it.hasExtraInfo())
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        (intent?.getParcelableExtra(EXTRA_APPROVE_SESSION) as? ApproveSession)?.let {
            onApproveAuth(it,it.hasExtraInfo())
        }
    }

    private fun showSelectConfigurationUI() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, FragmentConfiguration())
            .commit()
    }

    override fun onApproveAuth(session: ApproveSession, hasExtraInfo: Boolean) {
        showDialog(
            "approve",
            "Would you like to approve the request?${session.toDialogMessage()}",
            "Approve",
            { approveAuth(session) },
            "Deny",
            { rejectAuth(session) })
    }

    override fun showLoading() {
        binding.progressLayout.isVisible = true
    }

    override fun hideLoading() {
        binding.progressLayout.isVisible = false
    }

    override fun onConfigurationSelected(sdkConfiguration: SDKConfiguration) {
        try {
            if(!(application as App).sdkInitialized) {
                FuturaeSDK.INSTANCE.launch(
                    application,
                    sdkConfiguration
                )
                localStorage.persistSDKConfiguration(sdkConfiguration)
                (application as App).sdkInitialized = true
            }
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
                    getString(R.string.permission_rationale),
                    "ok",
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        } else {
                            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                        }
                    })
            }
        }

    }

}