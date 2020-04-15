package com.scibots.lakeapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class CancelEventReciever extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent != null) {
            String action = intent.getAction();

            if (action != null) {
                if(action.equals("CANCEL_ALERT")) {
                    Log.d("Cancel event","Clicked");
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                    preferences.edit().putString("cancel_event","true").commit();
                }
            }
        }
    }
}