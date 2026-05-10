package com.stricthabits.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, LockService.class);
        serviceIntent.putExtra("habit_name", intent.getStringExtra("habit_name"));
        serviceIntent.putExtra("habit_time", intent.getStringExtra("habit_time"));
        serviceIntent.putExtra("sound_enabled", intent.getBooleanExtra("sound_enabled", true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}