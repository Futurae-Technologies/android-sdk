package com.futurae.futuraedemo.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.ActivityHomeBinding
import com.futurae.futuraedemo.ui.fragment.FragmentSDKUnlockBio
import com.futurae.futuraedemo.ui.fragment.FragmentSDKUnlockBioCreds
import com.futurae.futuraedemo.ui.fragment.FragmentSDKUnlockBioPin
import com.futurae.futuraedemo.ui.fragment.FragmentSDKUnlockNone
import com.futurae.futuraedemo.ui.showDialog
import com.futurae.futuraedemo.ui.toDialogMessage
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.LockConfigurationType
import com.futurae.sdk.approve.ApproveSession

class HomeActivity : FuturaeActivity() {

    companion object {
        private const val EXTRA_LOCK_TYPE = "extra_lock_type"
        fun newIntent(
            context: Context,
            lockConfigurationType: LockConfigurationType,
            uri: String? = null
        ): Intent {
            return Intent(context, HomeActivity::class.java).apply {
                putExtra(EXTRA_LOCK_TYPE, lockConfigurationType)
                if (uri != null) {
                    putExtra(EXTRA_URI_STRING, uri)
                }
            }
        }
    }

    lateinit var binding: ActivityHomeBinding

    private val lockType: LockConfigurationType by lazy {
        intent.getSerializableExtra(EXTRA_LOCK_TYPE) as LockConfigurationType
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureUIByConfig()
    }

    @SuppressLint("NewApi")
    private fun configureUIByConfig() {
        val fragment: Fragment = when (lockType) {
            LockConfigurationType.NONE -> FragmentSDKUnlockNone()
            LockConfigurationType.BIOMETRICS_ONLY -> FragmentSDKUnlockBio()
            LockConfigurationType.BIOMETRICS_OR_DEVICE_CREDENTIALS -> FragmentSDKUnlockBioCreds()
            LockConfigurationType.SDK_PIN_WITH_BIOMETRICS_OPTIONAL -> FragmentSDKUnlockBioPin()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }


    override fun onApproveAuth(session: ApproveSession, hasExtraInfo: Boolean) {
        // Approving is a locked operation. Make sure to unlock if necessary.
        showDialog(
            "approve",
            "Would you like to approve the request?${session.toDialogMessage()}",
            "Approve",
            { approveAuth(session) },
            "Deny",
            { rejectAuth(session) })
    }

    override fun onReceivedUri(callback: () -> Unit) {
        if (FuturaeSDK.getClient().isLocked) {
            //unlock with preferred method and upon success invoke callback()
        }  else {
            callback()
        }
    }

}