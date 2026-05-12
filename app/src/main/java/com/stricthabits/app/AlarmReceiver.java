package com.stricthabits.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String habitName = intent.getStringExtra("habit_name");
        String habitTime = intent.getStringExtra("habit_time");
        boolean soundEnabled = intent.getBooleanExtra("sound_enabled", true);

        // Загрузить привычку из SharedPreferences, чтобы получить дни недели
        Habit habit = findHabitByName(context, habitName);
        if (habit != null) {
            if (habit.isEnabled() && !isSkippedToday(habit) && shouldRunToday(habit)) {
                Intent serviceIntent = new Intent(context, LockService.class);
                serviceIntent.putExtra("habit_name", habit.getName());
                serviceIntent.putExtra("habit_time", habit.getTime());
                serviceIntent.putExtra("sound_enabled", habit.isSoundEnabled());
                serviceIntent.putExtra(LockService.EXTRA_LOCK_KIND, LockService.LOCK_KIND_HABIT);
                serviceIntent.putExtra(LockService.EXTRA_UNLOCK_MODE, LockService.UNLOCK_MODE_PHRASE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
            // Планируем следующий запуск
            if (!isOneShot(habit)) {
                HabitScheduler.scheduleNext(context, habit);
            }
        }
    }

    private Habit findHabitByName(Context context, String name) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE);
            String json = prefs.getString("list", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                if (obj.getString("name").equals(name)) {
                    String time = obj.getString("time");
                    boolean sound = obj.getBoolean("soundEnabled");
                    boolean enabled = obj.optBoolean("enabled", true);
                    String skippedDate = obj.optString("skippedDate", "");
                    java.util.Map<String, Boolean> days = new java.util.HashMap<>();
                    org.json.JSONObject daysObj = obj.getJSONObject("days");
                    String[] dayKeys = {"mon","tue","wed","thu","fri","sat","sun"};
                    for (String key : dayKeys) {
                        days.put(key, daysObj.optBoolean(key, false));
                    }
                    Habit habit = new Habit(name, time, sound, days);
                    habit.setEnabled(enabled);
                    habit.setSkippedDate(skippedDate);
                    return habit;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private boolean isTimeMatch(String habitTime) {
        try {
            if (habitTime == null || !habitTime.contains(":")) return false;
            String[] parts = habitTime.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);

            java.util.Calendar now = java.util.Calendar.getInstance();
            int nowH = now.get(java.util.Calendar.HOUR_OF_DAY);
            int nowM = now.get(java.util.Calendar.MINUTE);

            int diff = Math.abs((nowH * 60 + nowM) - (h * 60 + m));
            return diff <= 5; // Разница не более 5 минут
        } catch (Exception e) { return false; }
    }

    private boolean shouldRunToday(Habit habit) {
        if (isOneShot(habit)) {
            return true;
        }

        Calendar cal = Calendar.getInstance();
        String dayKey;
        switch (cal.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY: dayKey = "mon"; break;
            case Calendar.TUESDAY: dayKey = "tue"; break;
            case Calendar.WEDNESDAY: dayKey = "wed"; break;
            case Calendar.THURSDAY: dayKey = "thu"; break;
            case Calendar.FRIDAY: dayKey = "fri"; break;
            case Calendar.SATURDAY: dayKey = "sat"; break;
            case Calendar.SUNDAY: dayKey = "sun"; break;
            default: return false;
        }
        Map<String, Boolean> days = habit.getDays();
        return days.getOrDefault(dayKey, false);
    }

    private boolean isSkippedToday(Habit habit) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return today.equals(habit.getSkippedDate());
    }

    private boolean isOneShot(Habit habit) {
        Map<String, Boolean> days = habit.getDays();
        return days == null || !days.containsValue(true);
    }
}
