package com.futurae.futuraedemo;

import android.app.Application;
import com.futurae.sdk.FuturaeClient;

public class AppMain extends Application {

    // overrides
    @Override
    public final void onCreate() {

        super.onCreate();

        FuturaeClient.launch(this);
    }
}
