package com.futurae.futuraedemo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.futurae.sdk.LockConfigurationType
import com.futurae.sdk.SDKConfiguration

private const val SP_NAME = "SP"
private const val SP_KEY_LOCK_CONFIGURATION = "SP_LC"
private const val SP_KEY_DURATION = "SP_D"
private const val SP_KEY_INVALIDATE_BY_BIOMETRICS = "SP_IBB"

class LocalStorage constructor(private val context: Context) {

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
            .build()
    }

    fun reset() = sharedPrefs.edit {
        this.clear()
    }

}