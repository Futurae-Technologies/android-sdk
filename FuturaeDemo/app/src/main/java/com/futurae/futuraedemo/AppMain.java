package com.futurae.futuraedemo;

import android.app.Application;
import com.futurae.sdk.FuturaeClient;
import com.futurae.sdk.Kit;

public class AppMain extends Application {

    // overrides
    @Override
    public final void onCreate() {

        super.onCreate();

        FuturaeClient.launch(this, (Kit)null);
    }
}
