package com.stricthabits.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("name");
                String time = obj.getString("time");
                boolean sound = obj.getBoolean("sound");
                JSONObject daysObj = obj.getJSONObject("days");
                Map<String, Boolean> days = new HashMap<>();
                days.put("Mon", daysObj.getBoolean("Mon"));
                days.put("Tue", daysObj.getBoolean("Tue"));
                days.put("Wed", daysObj.getBoolean("Wed"));
                days.put("Thu", daysObj.getBoolean("Thu"));
                days.put("Fri", daysObj.getBoolean("Fri"));
                days.put("Sat", daysObj.getBoolean("Sat"));
                days.put("Sun", daysObj.getBoolean("Sun"));
                Habit habit = new Habit(name, time, sound, days);
                HabitScheduler.scheduleOnce(context, habit);
            }
        } catch (Exception e) {}
    }
}