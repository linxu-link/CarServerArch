package com.fwk.service;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

public class SimpleApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        startService();
    }

    private void startService() {
        // start swu
        Intent startServiceIntent = new Intent(this, SimpleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(startServiceIntent);
        } else {
            startService(startServiceIntent);
        }
    }
}
