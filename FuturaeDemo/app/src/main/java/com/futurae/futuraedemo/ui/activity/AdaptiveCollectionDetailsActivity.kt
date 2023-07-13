package com.futurae.futuraedemo.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import com.futurae.futuraedemo.databinding.ActivityAdaptiveCollectionBinding
import com.futurae.sdk.adaptive.model.AdaptiveCollection
import com.google.gson.GsonBuilder

class AdaptiveCollectionDetailsActivity : FuturaeActivity() {

    companion object {
        private const val EXTRA_COLLECTION = "EXTRA_COLLECTION"

        fun newIntent(context: Context, collection: AdaptiveCollection): Intent {
            return Intent(context, AdaptiveCollectionDetailsActivity::class.java).apply {
                putExtra(EXTRA_COLLECTION, collection)
            }
        }
    }

    lateinit var binding: ActivityAdaptiveCollectionBinding

    override fun showLoading() {
        binding.progressLayout.isVisible = true
    }

    override fun hideLoading() {
        binding.progressLayout.isVisible = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdaptiveCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gson = GsonBuilder().setPrettyPrinting().setLenient().create()

        intent.getParcelableExtra<AdaptiveCollection>(EXTRA_COLLECTION)?.let {
            binding.adaptiveCollectionJsonText.text = gson.toJson(it)
        }
    }
}