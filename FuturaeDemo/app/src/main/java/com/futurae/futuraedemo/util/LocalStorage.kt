package com.futurae.futuraedemo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.futurae.sdk.public_api.common.LockConfigurationType
import com.futurae.sdk.public_api.common.SDKConfiguration

private const val SP_NAME = "SP"
private const val SP_KEY_LOCK_CONFIGURATION = "SP_LC"
private const val SP_KEY_DURATION = "SP_D"
private const val SP_KEY_INVALIDATE_BY_BIOMETRICS = "SP_IBB"
private const val SP_KEY_REQUIRE_DEVICE_UNLOCKED = "SP_RDU"
private const val SP_KEY_SKIP_HARDWARE_SECURITY = "SP_SHS"

class LocalStorage(private val context: Context) {

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    fun hasExistingConfiguration(): Boolean {
        return sharedPrefs.getString(SP_KEY_LOCK_CONFIGURATION, null) != null
                && sharedPrefs.getInt(SP_KEY_DURATION, -1) >= 0
    }

    fun persistSDKConfiguration(config: SDKConfiguration) {
        sharedPrefs.edit {
            putString(SP_KEY_LOCK_CONFIGURATION, config.lockConfigurationType.name)
            putInt(SP_KEY_DURATION, config.unlockDuration)
            putBoolean(SP_KEY_INVALIDATE_BY_BIOMETRICS, config.invalidatedByBiometricChange)
            putBoolean(SP_KEY_REQUIRE_DEVICE_UNLOCKED, config.unlockedDeviceRequired)
            putBoolean(SP_KEY_SKIP_HARDWARE_SECURITY, config.skipHardwareSecurity)
        }
    }

    fun getPersistedSDKConfig(): SDKConfiguration {
        return SDKConfiguration.Builder()
            .setLockConfigurationType(
                LockConfigurationType.valueOf(
                    sharedPrefs.getString(SP_KEY_LOCK_CONFIGURATION, "") ?: ""
                )
            )
            .setUnlockDuration(sharedPrefs.getInt(SP_KEY_DURATION, -1))
            .setInvalidatedByBiometricChange(sharedPrefs.getBoolean(SP_KEY_INVALIDATE_BY_BIOMETRICS, false))
            .setUnlockedDeviceRequired(sharedPrefs.getBoolean(SP_KEY_REQUIRE_DEVICE_UNLOCKED, false))
            .setSkipHardwareSecurity(sharedPrefs.getBoolean(SP_KEY_SKIP_HARDWARE_SECURITY, false))
            .build()
    }

    fun reset() = sharedPrefs.edit {
        this.clear()
    }

}