package com.futurae.futuraedemo.ui.activeunlockmethodspicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.futurae.futuraedemo.databinding.BottomSheetActiveUnlockMethodsPickerBinding
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.lock.model.UnlockMethodType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class ActiveUnlockMethodPickerSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "UnlockMethodPickerSheet"
    }

    lateinit var binding: BottomSheetActiveUnlockMethodsPickerBinding

    private var listener: Listener? = null

    fun setOnActiveUnlockMethodPickedListener(listener: Listener) = apply {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetActiveUnlockMethodsPickerBinding.inflate(layoutInflater, container, false)
        bindViews()
        return binding.root
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun bindViews() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ActiveUnlockMethodsAdapter(
                FuturaeSDK.client.lockApi.getActiveUnlockMethods()
            ) {
                listener?.onUnlockMethodSelected(it)
                dismiss()
            }
        }
    }
    interface Listener {
        fun onUnlockMethodSelected(unlockMethod: UnlockMethodType)
    }
}