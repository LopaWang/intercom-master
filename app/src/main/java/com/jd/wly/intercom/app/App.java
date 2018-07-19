package com.jd.wly.intercom.app;

import android.app.Application;
import android.content.Context;

public class App extends Application {

    private static Context context;

    public static Context getInstance() {
        return context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }
}
