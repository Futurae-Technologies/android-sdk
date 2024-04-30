package com.futurae.futuraedemo.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.futurae.futuraedemo.databinding.ActivityAdaptiveOverviewBinding
import com.futurae.futuraedemo.databinding.ItemAdaptiveCollectionBinding
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.adaptive.AdaptiveDbHelper
import com.futurae.sdk.adaptive.model.AdaptiveCollection
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdaptiveViewerActivity : FuturaeActivity() {

    lateinit var binding: ActivityAdaptiveOverviewBinding

    private val adapter = AdaptiveCollectionAdapter {
        startActivity(
            AdaptiveCollectionDetailsActivity.newIntent(this, it)
        )
    }

    override fun showLoading() {
        binding.progressLayout.isVisible = true
    }

    override fun hideLoading() {
        binding.progressLayout.isVisible = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdaptiveOverviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.adapter = adapter
        binding.clearCollections.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                AdaptiveDbHelper.deleteAllCollections()
            }
        }

        lifecycleScope.launch(Dispatchers.Main) {
            AdaptiveDbHelper.getAllCollections().collect {
                binding.emptyText.isVisible = it.isEmpty()
                binding.recycler.isVisible = it.isNotEmpty()
                binding.clearCollections.isVisible = it.isNotEmpty()
                adapter.submitList(it.sortedBy { coll -> coll.timestamp })

                if(it.isEmpty()) {
                    if(FuturaeSDK.client.accountApi.getActiveAccounts().isEmpty()) {
                        binding.emptyText.text = "You must enroll an account before gathering collections"
                    } else {
                        binding.emptyText.text = "No collections yet"
                    }
                }
            }
        }

    }
}

class AdaptiveCollectionAdapter(
    private val onItemTapped: (AdaptiveCollection) -> Unit
) : ListAdapter<AdaptiveCollection, AdaptiveCollectionViewHolder>(callback) {

    private val gson = GsonBuilder().setLenient().create()
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    companion object {
        val callback = object : DiffUtil.ItemCallback<AdaptiveCollection>() {
            override fun areItemsTheSame(
                oldItem: AdaptiveCollection,
                newItem: AdaptiveCollection
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: AdaptiveCollection,
                newItem: AdaptiveCollection
            ): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdaptiveCollectionViewHolder {
        return AdaptiveCollectionViewHolder(
            ItemAdaptiveCollectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        ).apply {
            this.binding.root.setOnClickListener {
                onItemTapped(getItem(adapterPosition))
            }
        }
    }

    override fun onBindViewHolder(holder: AdaptiveCollectionViewHolder, position: Int) {
        val collection = getItem(position)
        holder.binding.jsonText.text = gson.toJson(collection)
        holder.binding.dateText.text = simpleDateFormat.format(collection.timestamp * 1000)
    }

}

class AdaptiveCollectionViewHolder(val binding: ItemAdaptiveCollectionBinding) :
    RecyclerView.ViewHolder(binding.root)