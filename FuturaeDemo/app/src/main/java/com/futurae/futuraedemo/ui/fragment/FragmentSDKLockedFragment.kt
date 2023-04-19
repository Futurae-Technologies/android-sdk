package com.futurae.futuraedemo.ui.fragment

import android.os.CountDownTimer
import android.widget.TextView

abstract class FragmentSDKLockedFragment : FragmentSDKOperations() {

    private var unlockTimer: CountDownTimer? = null

    fun onUnlocked(timeLeftView: TextView, statusView: TextView) {
        statusView.text = "Unlocked"
        unlockTimer?.cancel()
        unlockTimer =
            object : CountDownTimer(localStorage.getPersistedSDKConfig().unlockDuration * 1000L, 500L) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftView.text = "${millisUntilFinished / 1000} sec"
                }

                override fun onFinish() {
                    statusView.text = "Locked"
                }
            }.apply {
                start()
            }
    }

    fun onLocked(timeLeftView: TextView, statusView: TextView) {
        unlockTimer?.cancel()
        timeLeftView.text =  ""
        statusView.text = "Locked"
    }

}