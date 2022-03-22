package com.futurae.futuraedemo;

import android.app.Application;

import com.futurae.sdk.FuturaeClient;
import com.futurae.sdk.Kit;

import timber.log.Timber;

public class AppMain extends Application {

	// overrides
	@Override
	public final void onCreate() {

		super.onCreate();

		Timber.plant(new Timber.DebugTree());
    //adaptive is optional. If you want to use it, make sure you read up our documentation
		boolean adaptiveEnabled = true;
		FuturaeClient.launch(this, adaptiveEnabled, (Kit) null);
	}
}
