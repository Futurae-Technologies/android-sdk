package com.futurae.futuraedemo.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.fragment.FragmentConfiguration
import com.futurae.sdk.public_api.common.SDKConfiguration

const val EXTRA_CONFIG = "EXTRA_CONFIG"

class ActivitySDKConfiguration : AppCompatActivity(), FragmentConfiguration.Listener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FragmentConfiguration())
            .commit()
    }

    override fun onConfigurationSelected(sdkConfiguration: SDKConfiguration) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_CONFIG, sdkConfiguration)
        })
        finish()
    }


}