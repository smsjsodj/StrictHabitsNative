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
            String json = prefs.getString("list", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("name");
                String time = obj.getString("time");
                boolean sound = obj.getBoolean("soundEnabled");
                Map<String, Boolean> days = new HashMap<>();
                JSONObject daysObj = obj.getJSONObject("days");
                days.put("mon", daysObj.optBoolean("mon", false));
                days.put("tue", daysObj.optBoolean("tue", false));
                days.put("wed", daysObj.optBoolean("wed", false));
                days.put("thu", daysObj.optBoolean("thu", false));
                days.put("fri", daysObj.optBoolean("fri", false));
                days.put("sat", daysObj.optBoolean("sat", false));
                days.put("sun", daysObj.optBoolean("sun", false));
                Habit habit = new Habit(name, time, sound, days);
                HabitScheduler.schedule(context, habit);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}