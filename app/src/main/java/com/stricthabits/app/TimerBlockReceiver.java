package com.stricthabits.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class TimerBlockReceiver extends BroadcastReceiver {
    private static final String TAG = "TimerBlockReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String blockName = intent.getStringExtra("block_name");
        String startTime = intent.getStringExtra("start_time");
        String endTime = intent.getStringExtra("end_time");

        Log.d(TAG, "Timer block triggered: " + blockName + " from " + startTime + " to " + endTime);

        // Вычисляем длительность в минутах
        int durationMinutes = calculateDuration(startTime, endTime);

        // Запускаем TimerLockService
        Intent serviceIntent = new Intent(context, TimerLockService.class);
        serviceIntent.putExtra("lock_name", blockName);
        serviceIntent.putExtra("duration_minutes", durationMinutes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        Log.d(TAG, "Started timer lock service for " + durationMinutes + " minutes");
    }

    private int calculateDuration(String startTime, String endTime) {
        try {
            String[] startParts = startTime.split(":");
            String[] endParts = endTime.split(":");

            int startHour = Integer.parseInt(startParts[0]);
            int startMinute = Integer.parseInt(startParts[1]);
            int endHour = Integer.parseInt(endParts[0]);
            int endMinute = Integer.parseInt(endParts[1]);

            int startTotalMinutes = startHour * 60 + startMinute;
            int endTotalMinutes = endHour * 60 + endMinute;

            int duration = endTotalMinutes - startTotalMinutes;

            // Если конец меньше начала, значит переход через полночь
            if (duration < 0) {
                duration += 24 * 60;
            }

            return duration > 0 ? duration : 60; // Минимум 60 минут
        } catch (Exception e) {
            Log.e(TAG, "Error calculating duration", e);
            return 60; // По умолчанию 60 минут
        }
    }
}
