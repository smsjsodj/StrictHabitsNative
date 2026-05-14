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
                boolean enabled = obj.optBoolean("enabled", true);
                String skippedDate = obj.optString("skippedDate", "");
                String lastCompletedDate = obj.optString("lastCompletedDate", "");
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
                habit.setEnabled(enabled);
                habit.setSkippedDate(skippedDate);
                habit.setLastCompletedDate(lastCompletedDate);
                HabitScheduler.schedule(context, habit);
            }
            // Загрузить и запланировать блокировки (если есть)
            try {
                String blocksJson = prefs.getString("blocks", "[]");
                org.json.JSONArray barr = new org.json.JSONArray(blocksJson);
                for (int i = 0; i < barr.length(); i++) {
                    org.json.JSONObject obj = barr.getJSONObject(i);
                    String start = obj.optString("startTime", "00:00");
                    String end = obj.optString("endTime", "00:00");
                    java.util.Map<String, Boolean> bdays = new java.util.HashMap<>();
                    org.json.JSONObject daysObj = obj.optJSONObject("days");
                    if (daysObj != null) {
                        bdays.put("mon", daysObj.optBoolean("mon", false));
                        bdays.put("tue", daysObj.optBoolean("tue", false));
                        bdays.put("wed", daysObj.optBoolean("wed", false));
                        bdays.put("thu", daysObj.optBoolean("thu", false));
                        bdays.put("fri", daysObj.optBoolean("fri", false));
                        bdays.put("sat", daysObj.optBoolean("sat", false));
                        bdays.put("sun", daysObj.optBoolean("sun", false));
                    }
                    BlockPeriod bp = new BlockPeriod(start, end, bdays);
                    bp.setEnabled(obj.optBoolean("enabled", true));
                    BlockScheduler.schedule(context, bp);
                }
            } catch (Exception e) { e.printStackTrace(); }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
