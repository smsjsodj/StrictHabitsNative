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
                try {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.optString("name", "").trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    int[] timeParts = TimeUtils.parseTimeParts(obj.optString("time", "12:00"), 12, 0);
                    String time = TimeUtils.format(timeParts[0], timeParts[1]);
                    boolean sound = obj.optBoolean("soundEnabled", true);
                    boolean enabled = obj.optBoolean("enabled", true);
                    String skippedDate = obj.optString("skippedDate", "");
                    String lastCompletedDate = obj.optString("lastCompletedDate", "");
                    Map<String, Boolean> days = new HashMap<>();
                    JSONObject daysObj = obj.optJSONObject("days");
                    days.put("mon", daysObj != null && daysObj.optBoolean("mon", false));
                    days.put("tue", daysObj != null && daysObj.optBoolean("tue", false));
                    days.put("wed", daysObj != null && daysObj.optBoolean("wed", false));
                    days.put("thu", daysObj != null && daysObj.optBoolean("thu", false));
                    days.put("fri", daysObj != null && daysObj.optBoolean("fri", false));
                    days.put("sat", daysObj != null && daysObj.optBoolean("sat", false));
                    days.put("sun", daysObj != null && daysObj.optBoolean("sun", false));
                    Habit habit = new Habit(name, time, sound, days);
                    habit.setEnabled(enabled);
                    habit.setSkippedDate(skippedDate);
                    habit.setLastCompletedDate(lastCompletedDate);
                    HabitScheduler.schedule(context, habit);
                } catch (Exception e) { e.printStackTrace(); }
            }
            // Загрузить и запланировать блокировки (если есть)
            try {
                String blocksJson = prefs.getString("blocks", "[]");
                org.json.JSONArray barr = new org.json.JSONArray(blocksJson);
                for (int i = 0; i < barr.length(); i++) {
                    org.json.JSONObject obj = barr.getJSONObject(i);
                    int[] startParts = TimeUtils.parseTimeParts(obj.optString("startTime", "00:00"), 0, 0);
                    int[] endParts = TimeUtils.parseTimeParts(obj.optString("endTime", "00:00"), 0, 0);
                    String start = TimeUtils.format(startParts[0], startParts[1]);
                    String end = TimeUtils.format(endParts[0], endParts[1]);
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
            // Загрузить и запланировать таймер-блокировки
            try {
                String timerBlocksJson = prefs.getString("timer_blocks", "[]");
                org.json.JSONArray tarr = new org.json.JSONArray(timerBlocksJson);
                for (int i = 0; i < tarr.length(); i++) {
                    org.json.JSONObject obj = tarr.getJSONObject(i);
                    int[] startParts = TimeUtils.parseTimeParts(obj.optString("startTime", "00:00"), 0, 0);
                    int[] endParts = TimeUtils.parseTimeParts(obj.optString("endTime", "00:00"), 0, 0);
                    String start = TimeUtils.format(startParts[0], startParts[1]);
                    String end = TimeUtils.format(endParts[0], endParts[1]);
                    java.util.Map<String, Boolean> tdays = new java.util.HashMap<>();
                    org.json.JSONObject daysObj = obj.optJSONObject("days");
                    if (daysObj != null) {
                        tdays.put("mon", daysObj.optBoolean("mon", false));
                        tdays.put("tue", daysObj.optBoolean("tue", false));
                        tdays.put("wed", daysObj.optBoolean("wed", false));
                        tdays.put("thu", daysObj.optBoolean("thu", false));
                        tdays.put("fri", daysObj.optBoolean("fri", false));
                        tdays.put("sat", daysObj.optBoolean("sat", false));
                        tdays.put("sun", daysObj.optBoolean("sun", false));
                    }
                    TimerBlock tb = new TimerBlock(obj.optString("name", "Таймер"), start, end, tdays);
                    tb.setEnabled(obj.optBoolean("enabled", true));
                    TimerBlockScheduler.schedule(context, tb);
                }
            } catch (Exception e) { e.printStackTrace(); }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
