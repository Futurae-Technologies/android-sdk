package com.futurae.futuraedemo.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.futurae.futuraedemo.databinding.ActivityHistoryBinding
import com.futurae.futuraedemo.databinding.ItemAccountHistoryBinding
import com.futurae.futuraedemo.util.showErrorAlert
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.account.model.AccountHistoryItem
import com.futurae.sdk.public_api.exception.FTException
import kotlinx.coroutines.launch
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
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )
        FuturaeSDK.client.accountApi.getActiveAccounts().firstOrNull()?.let {
            lifecycleScope.launch {
                try {
                    val accountHistoryItems =
                        FuturaeSDK.client.accountApi.getAccountHistory(it.userId).await()
                    if (accountHistoryItems.isNotEmpty()) {
                        binding.progress.isVisible = false
                        binding.emptyText.isVisible = false
                        binding.recyclerView.isVisible = true
                    } else {
                        binding.progress.isVisible = false
                        binding.emptyText.isVisible = true
                        binding.recyclerView.isVisible = false
                    }
                    adapter.submitList(accountHistoryItems)
                } catch (e: FTException) {
                    showErrorAlert("SDK Error", e)
                }

            }
        }

    }

    override fun showLoading() {
        binding.progress.isVisible = true
    }

    override fun hideLoading() {
        binding.progress.isVisible = false
    }
}

class AccountHistoryAdapter : ListAdapter<AccountHistoryItem, AccountHistoryViewHolder>(callback) {

    private val simpleDateFormat =
        SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z", Locale.getDefault())

    companion object {
        val callback = object : DiffUtil.ItemCallback<AccountHistoryItem>() {
            override fun areItemsTheSame(
                oldItem: AccountHistoryItem,
                newItem: AccountHistoryItem
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: AccountHistoryItem,
                newItem: AccountHistoryItem
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

    fun bind(history: AccountHistoryItem, date: String) {
        itemAccountHistoryBinding.typeValueText.text = history.details.type
        itemAccountHistoryBinding.statusValueText.text = history.details.result.toString()
        itemAccountHistoryBinding.dateValueText.text = date
        itemAccountHistoryBinding.resultValueText.text = history.details.result
    }
}