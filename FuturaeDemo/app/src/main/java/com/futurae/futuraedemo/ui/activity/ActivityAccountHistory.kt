package com.futurae.futuraedemo.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.futurae.futuraedemo.FuturaeSdkWrapper
import com.futurae.futuraedemo.databinding.ActivityHistoryBinding
import com.futurae.futuraedemo.databinding.ItemAccountHistoryBinding
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.Callback
import com.futurae.sdk.model.AccountHistory
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityAccountHistory : FuturaeActivity() {

    lateinit var binding: ActivityHistoryBinding

    private val adapter = AccountHistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.progress.isVisible = true
        binding.emptyText.isVisible = false
        binding.recyclerView.isVisible = false
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        FuturaeSdkWrapper.client.accounts.firstOrNull()?.let {
            FuturaeSdkWrapper.client.getAccountHistory(it.userId, object : Callback<List<AccountHistory>> {
                override fun onSuccess(result: List<AccountHistory>) {
                    if (result.isNotEmpty()) {
                        binding.progress.isVisible = false
                        binding.emptyText.isVisible = false
                        binding.recyclerView.isVisible = true
                    } else {
                        binding.progress.isVisible = false
                        binding.emptyText.isVisible = true
                        binding.recyclerView.isVisible = false
                    }
                    adapter.submitList(result)
                }

                override fun onError(throwable: Throwable) {
                    binding.progress.isVisible = false
                    binding.emptyText.isVisible = true
                    binding.recyclerView.isVisible = false
                    showErrorAlert("Error fetching history", throwable)
                }
            })
        }

    }

    override fun showLoading() {
        binding.progress.isVisible = true
    }

    override fun hideLoading() {
        binding.progress.isVisible = false
    }
}

class AccountHistoryAdapter : ListAdapter<AccountHistory, AccountHistoryViewHolder>(callback) {

    private val simpleDateFormat = SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z", Locale.getDefault())

    companion object {
        val callback = object : DiffUtil.ItemCallback<AccountHistory>() {
            override fun areItemsTheSame(
                oldItem: AccountHistory,
                newItem: AccountHistory
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: AccountHistory,
                newItem: AccountHistory
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountHistoryViewHolder {
        return AccountHistoryViewHolder(
            ItemAccountHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: AccountHistoryViewHolder, position: Int) {
        //timestamp is in seconds
        holder.bind(getItem(position), simpleDateFormat.format(getItem(position).timestamp * 1000))
    }

}

class AccountHistoryViewHolder(private val itemAccountHistoryBinding: ItemAccountHistoryBinding) :
    RecyclerView.ViewHolder(itemAccountHistoryBinding.root) {

    fun bind(history: AccountHistory, date: String) {
        itemAccountHistoryBinding.typeValueText.text = history.details.type
        itemAccountHistoryBinding.statusValueText.text = history.details.isSuccess().toString()
        itemAccountHistoryBinding.dateValueText.text = date
        itemAccountHistoryBinding.resultValueText.text = history.details.result
    }
}