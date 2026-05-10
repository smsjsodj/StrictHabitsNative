package com.stricthabits.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Habit habit = new Habit(obj.getString("name"), obj.getString("time"),
                        obj.getBoolean("telegramOnly"), obj.getBoolean("soundEnabled"));
                HabitScheduler.schedule(context, habit);
            }
        } catch (Exception e) {}
        // Запускаем Telegram сервис
        Intent tgIntent = new Intent(context, TelegramService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(tgIntent);
        } else {
            context.startService(tgIntent);
        }
    }
}