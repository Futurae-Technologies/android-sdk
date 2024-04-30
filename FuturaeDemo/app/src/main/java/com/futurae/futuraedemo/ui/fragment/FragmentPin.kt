package com.futurae.futuraedemo.ui.fragment

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.futurae.futuraedemo.databinding.FragmentPinBinding


class FragmentPin : Fragment() {

	lateinit var binding: FragmentPinBinding

	var listener: Listener? = null

	private val inputMethodManager by lazy {
		requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		binding = FragmentPinBinding.inflate(layoutInflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.pinLockView.inputType = InputType.TYPE_CLASS_NUMBER
		binding.pinLockView.isPasswordHidden = true
		binding.pinLockView.showSoftInputOnFocus = true
		binding.pinLockView.requestFocus()
		view.post {
			inputMethodManager?.showSoftInput(binding.pinLockView, InputMethodManager.SHOW_IMPLICIT)
		}
		binding.pinLockView.addTextChangedListener { text ->
			val newText = text.toString().toCharArray()

			if (newText.count() < 6) {
				return@addTextChangedListener
			}

			listener?.onPinComplete(newText)
		}
	}

	interface Listener {
		fun onPinComplete(pin: CharArray)
	}
}