package com.futurae.futuraedemo.ui.activeunlockmethodspicker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futurae.futuraedemo.databinding.ActiveUnlockMethodItemBinding
import com.futurae.sdk.public_api.lock.model.UnlockMethodType

class ActiveUnlockMethodsAdapter(
    private val activeUnlockMethods: List<UnlockMethodType>,
    private val itemSelectionListener: (UnlockMethodType) -> Unit
) : RecyclerView.Adapter<ActiveUnlockMethodsAdapter.ActiveUnlockMethodsViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ActiveUnlockMethodsViewHolder {
        val binding = ActiveUnlockMethodItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ActiveUnlockMethodsViewHolder(binding).apply {
            binding.root.setOnClickListener {
                itemSelectionListener(activeUnlockMethods[adapterPosition])
            }
        }
    }

    override fun getItemCount(): Int = activeUnlockMethods.size

    override fun onBindViewHolder(holder: ActiveUnlockMethodsViewHolder, position: Int) {
        val unlockMethod = activeUnlockMethods[position]
        holder.binding.titleText.text = unlockMethod.name
    }


    inner class ActiveUnlockMethodsViewHolder(
        val binding: ActiveUnlockMethodItemBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
