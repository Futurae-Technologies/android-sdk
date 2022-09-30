package com.futurae.futuraedemo.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.andrognito.pinlockview.PinLockListener
import com.futurae.futuraedemo.databinding.FragmentPinBinding

class FragmentPin : Fragment() {

	lateinit var binding: FragmentPinBinding

	var listener: Listener? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		binding = FragmentPinBinding.inflate(layoutInflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.pinLockView.attachIndicatorDots(binding.indicatorDots)
		binding.pinLockView.setPinLockListener(object : PinLockListener {
			override fun onComplete(pin: String) {
				listener?.onPinComplete(pin.toCharArray())
			}

			override fun onEmpty() {
				//no-op
			}

			override fun onPinChange(pinLength: Int, intermediatePin: String) {
				//no-op
			}

		})
	}

	interface Listener {
		fun onPinComplete(pin: CharArray)
	}
}