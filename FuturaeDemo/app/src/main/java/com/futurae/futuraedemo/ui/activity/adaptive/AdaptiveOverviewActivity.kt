package com.futurae.futuraedemo.ui.activity.adaptive

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.ActivityAdaptiveOverviewBinding
import com.futurae.futuraedemo.ui.activity.FuturaeActivity
import com.futurae.futuraedemo.util.showAlert
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.adaptive.AdaptiveSDK
import com.google.android.material.slider.Slider

class AdaptiveOverviewActivity : FuturaeActivity() {

    lateinit var binding: ActivityAdaptiveOverviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdaptiveOverviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.adaptiveToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestAdaptivePermissions()
                FuturaeSDK.client.adaptiveApi.enableAdaptive(application)
            } else {
                FuturaeSDK.client.adaptiveApi.disableAdaptive()
            }
            updateToggles()
        }
        binding.adaptiveAuthToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                FuturaeSDK.client.adaptiveApi.enableAdaptiveSubmissionOnAuthentication()
            } else {
                FuturaeSDK.client.adaptiveApi.disableAdaptiveSubmissionOnAuthentication()
            }
        }
        binding.adaptiveMigrationToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                FuturaeSDK.client.adaptiveApi.enableAdaptiveSubmissionOnAccountMigration()
            } else {
                FuturaeSDK.client.adaptiveApi.disableAdaptiveSubmissionOnAccountMigration()
            }
        }
        binding.buttonAdaptiveCollections.setOnClickListener {
            startActivity(Intent(this, AdaptiveCollectionsOverviewActivity::class.java))
        }
        binding.buttonAdaptiveThreshold.setOnClickListener {
            if (FuturaeSDK.client.adaptiveApi.isAdaptiveEnabled()) {
                var sliderValue = AdaptiveSDK.getAdaptiveCollectionThreshold()
                val dialogView = layoutInflater.inflate(R.layout.dialog_adaptive_time, null)
                val textValue = dialogView.findViewById<TextView>(R.id.sliderValue).apply {
                    text = "$sliderValue sec"
                }
                dialogView.findViewById<Slider>(R.id.slider).apply {
                    value = sliderValue.toFloat()
                    addOnChangeListener { _, value, _ ->
                        sliderValue = value.toInt()
                        textValue.text = "${value.toInt()} sec"
                    }
                }
                val dialog = AlertDialog.Builder(
                    this,
                    com.google.android.material.R.style.Theme_Material3_Light_Dialog
                )
                    .setTitle("Adaptive time threshold").setView(dialogView)
                    .setPositiveButton("OK") { _, _ ->
                        AdaptiveSDK.setAdaptiveCollectionThreshold(sliderValue)
                        binding.adaptiveThresholdValueTextview.text = AdaptiveSDK.getAdaptiveCollectionThreshold().toString() + " sec"
                    }.create()
                dialog.show()
            } else {
                showAlert("Adaptive SDK", "Please initialize/enable Adaptive SDK first")
            }
        }
        binding.adaptiveThresholdValueTextview.text = if(FuturaeSDK.client.adaptiveApi.isAdaptiveEnabled()) {
            AdaptiveSDK.getAdaptiveCollectionThreshold().toString() + " sec"
        } else {
            "-"
        }
        updateToggles()
    }

    private fun updateToggles() {
        val isAdaptiveEnabled = FuturaeSDK.client.adaptiveApi.isAdaptiveEnabled()
        binding.adaptiveToggle.isChecked = isAdaptiveEnabled
        binding.adaptiveAuthToggle.isEnabled = isAdaptiveEnabled
        binding.adaptiveMigrationToggle.isEnabled = isAdaptiveEnabled
        binding.adaptiveAuthToggle.isChecked =
            FuturaeSDK.client.adaptiveApi.isAdaptiveSubmissionOnAuthenticationEnabled()
        binding.adaptiveMigrationToggle.isChecked =
            FuturaeSDK.client.adaptiveApi.isAdaptiveSubmissionOnAccountMigrationEnabled()
    }

    fun requestAdaptivePermissions() = permissionLauncher.launch(getAdaptivePermissions())

    private fun getAdaptivePermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.toTypedArray()
    }
}