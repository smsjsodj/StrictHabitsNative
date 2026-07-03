package com.stricthabits.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.Map;

public class TimerBlockScheduler {
    private static final String TAG = "TimerBlockScheduler";

    public static void schedule(Context context, TimerBlock block) {
        if (!block.isEnabled()) {
            Log.d(TAG, "Timer block disabled, skipping: " + block.getName());
            return;
        }

        Map<String, Boolean> days = block.getDays();
        if (days == null || !days.containsValue(true)) {
            Log.d(TAG, "No days selected for timer block: " + block.getName());
            return;
        }

        Calendar now = Calendar.getInstance();
        int currentDay = now.get(Calendar.DAY_OF_WEEK);

        for (int i = 0; i < 7; i++) {
            int checkDay = (currentDay - 1 + i) % 7;
            String dayKey = getDayKey(checkDay == 0 ? 7 : checkDay);

            if (days.getOrDefault(dayKey, false)) {
                Calendar triggerTime = Calendar.getInstance();
                triggerTime.add(Calendar.DAY_OF_YEAR, i);

                String[] parts = block.getStartTime().split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                triggerTime.set(Calendar.HOUR_OF_DAY, hour);
                triggerTime.set(Calendar.MINUTE, minute);
                triggerTime.set(Calendar.SECOND, 0);

                if (i == 0 && triggerTime.before(now)) {
                    continue;
                }

                scheduleAlarm(context, block, triggerTime);
                Log.d(TAG, "Scheduled timer block: " + block.getName() + " at " + triggerTime.getTime());
                return;
            }
        }
    }

    private static void scheduleAlarm(Context context, TimerBlock block, Calendar triggerTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, TimerBlockReceiver.class);
        intent.putExtra("block_name", block.getName());
        intent.putExtra("start_time", block.getStartTime());
        intent.putExtra("end_time", block.getEndTime());

        int requestCode = getRequestCode(block);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime.getTimeInMillis(),
                            pendingIntent
                    );
                } else {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime.getTimeInMillis(),
                            pendingIntent
                    );
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime.getTimeInMillis(),
                        pendingIntent
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling timer block alarm", e);
        }
    }

    public static void cancel(Context context, TimerBlock block) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, TimerBlockReceiver.class);
        int requestCode = getRequestCode(block);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        Log.d(TAG, "Cancelled timer block: " + block.getName());
    }

    private static int getRequestCode(TimerBlock block) {
        return ("timer_" + block.getName() + "_" + block.getStartTime()).hashCode();
    }

    private static String getDayKey(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY: return "mon";
            case Calendar.TUESDAY: return "tue";
            case Calendar.WEDNESDAY: return "wed";
            case Calendar.THURSDAY: return "thu";
            case Calendar.FRIDAY: return "fri";
            case Calendar.SATURDAY: return "sat";
            case Calendar.SUNDAY: return "sun";
            default: return "mon";
        }
    }
}
