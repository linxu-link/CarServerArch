package com.fwk.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleService;

import com.fwk.service.binder.HvacBinder;

public class SimpleService extends LifecycleService {

    private static final String CHANNEL_ID_STRING = "SimpleService";
    private static final int CHANNEL_ID = 0x11;

    private HvacBinder mHvacBinder;

    @Override
    public void onCreate() {
        super.onCreate();
        startServiceForeground();
        mHvacBinder = new HvacBinder();
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return mHvacBinder;
    }

    private void startServiceForeground() {
        NotificationManager notificationManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            channel = new NotificationChannel(CHANNEL_ID_STRING, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(getApplicationContext(),
                    CHANNEL_ID_STRING).build();
            startForeground(CHANNEL_ID, notification);
        }
    }
}
