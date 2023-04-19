package com.futurae.futuraedemo.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.ui.fragment.FragmentPin

const val EXTRA_PIN = "EXTRA_PIN"

class ActivityPin : AppCompatActivity(), FragmentPin.Listener {

    companion object {
        fun newIntentForConfigChange(context: Context): Intent {
            return Intent(context, ActivityPin::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FragmentPin().apply {
                //TODO REFACTOR THIS LEAK
                listener = this@ActivityPin
            })
            .commit()
    }

    override fun onPinComplete(pin: CharArray) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_PIN, pin)
        })
        finish()
    }
}