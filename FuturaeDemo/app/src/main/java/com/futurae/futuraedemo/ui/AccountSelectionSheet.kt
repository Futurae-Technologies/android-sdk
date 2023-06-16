package com.futurae.futuraedemo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.R
import com.futurae.futuraedemo.databinding.BottomSheetUserListBinding
import com.futurae.futuraedemo.databinding.UserListItemBinding
import com.futurae.sdk.model.FTAccount
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class AccountSelectionSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "AccountSelectionSheet"
    }

    lateinit var binding: BottomSheetUserListBinding

    var listener: Listener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = BottomSheetUserListBinding.inflate(layoutInflater, container, false)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = UserListAdapter(
                FuturaeSdkWrapper.sdk.getClient().accounts
            ) {
                listener?.onAccountSelected(it)
                dismiss()
            }
        }
        return binding.root
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private inner class UserListAdapter(
        private val accounts: List<FTAccount>,
        private val itemSelectionListener: (String) -> Unit
    ) : RecyclerView.Adapter<UserListAdapter.UserViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val binding = UserListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return UserViewHolder(binding).apply {
                binding.root.setOnClickListener {
                    itemSelectionListener(accounts[adapterPosition].userId)
                }
            }
        }

        override fun getItemCount(): Int = accounts.size

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val ftAccount = accounts[position]
            holder.binding.titleText.text = ftAccount.username
            holder.binding.subtitleText.text = ftAccount.serviceId
            Glide.with(holder.binding.root.context)
                .load(ftAccount.serviceLogo)
                .into(holder.binding.serviceLogo)
        }


        inner class UserViewHolder(
            val binding: UserListItemBinding
        ) : RecyclerView.ViewHolder(binding.root)
    }

    interface Listener {
        fun onAccountSelected(userId: String)
    }
}