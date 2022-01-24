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
        boolean adaptiveEnabled = false;
        FuturaeClient.launch(this, adaptiveEnabled, (Kit)null);
    }
}
