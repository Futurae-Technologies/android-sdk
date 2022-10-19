package com.futurae.futuraedemo.ui

import android.app.Application
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.LockConfigurationType
import com.futurae.sdk.SDKConfiguration
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }

    fun launchFuturaeSDKWithConfig(config: LockConfigurationType, unlockDuration: Int) {
        //New Futurae SDK unlock API
        FuturaeSDK.launch(
            this, SDKConfiguration.Builder()
                .setLockConfigurationType(config)
                .setUnlockDuration(unlockDuration)
                .setInvalidatedByBiometricChange(false)
                .build()
        )
    }
}