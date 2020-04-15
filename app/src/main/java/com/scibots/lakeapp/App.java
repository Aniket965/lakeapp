package com.scibots.lakeapp;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {
    public static final String CHANNEL_ID = "HotWordServiceChannel";
    public static final String ALERT_CHANNEL_ID = "alert Channel";

    @Override
    public void onCreate() {
        super.onCreate();
        // Create Notifcation Channel for oreo and above
        CreateNotifcationChannel();
    }

    private void CreateNotifcationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {


            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "HotWordTrigger Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);

            NotificationChannel alertchannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Keyword Event Alert Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            manager.createNotificationChannel(alertchannel);

        }
    }
}