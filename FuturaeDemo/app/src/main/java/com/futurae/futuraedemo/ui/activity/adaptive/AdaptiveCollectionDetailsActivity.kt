package com.futurae.futuraedemo.ui.activity.adaptive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.futurae.futuraedemo.databinding.ActivityAdaptiveCollectionBinding
import com.futurae.futuraedemo.ui.activity.FuturaeActivity
import com.futurae.futuraedemo.util.getParcelable
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdaptiveCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gson = GsonBuilder().setPrettyPrinting().setLenient().create()

        intent.getParcelable(EXTRA_COLLECTION, AdaptiveCollection::class.java)?.let {
            binding.adaptiveCollectionJsonText.text = gson.toJson(it)
        }
    }
}