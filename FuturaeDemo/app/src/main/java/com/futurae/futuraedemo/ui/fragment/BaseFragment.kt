package com.futurae.futuraedemo.ui.fragment

import androidx.fragment.app.Fragment
import com.futurae.futuraedemo.R

abstract class BaseFragment : Fragment() {
    protected fun getPinWithCallback(callback: (CharArray) -> Unit) {
        val pinFragment = FragmentPin()
        parentFragmentManager.beginTransaction().add(R.id.pinFragmentContainer, pinFragment.apply {
            listener = object : FragmentPin.Listener {
                override fun onPinComplete(pin: CharArray) {
                    parentFragmentManager.beginTransaction().remove(pinFragment).commit()
                    callback(pin)
                }
            }
        }).commit()
    }
}