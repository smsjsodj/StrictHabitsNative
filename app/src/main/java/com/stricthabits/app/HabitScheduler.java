package com.stricthabits.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

public class HabitScheduler {
    public static void schedule(Context context, Habit habit) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || habit == null || !hasEnabledDay(habit)) {
            return;
        }

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("habit_name", habit.getName());
        intent.putExtra("habit_time", habit.getTime());
        intent.putExtra("sound_enabled", habit.isSoundEnabled());

        int requestCode = getRequestCode(habit);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = getNextTriggerTime(habit);
        if (calendar == null) {
            alarmManager.cancel(pendingIntent);
            return;
        }

        long triggerAtMillis = calendar.getTimeInMillis();
        alarmManager.cancel(pendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canUseExactAlarm(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        triggerAtMillis, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(createAlarmClockInfo(context, requestCode, triggerAtMillis),
                        pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                        triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP,
                        triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(createAlarmClockInfo(context, requestCode, triggerAtMillis),
                        pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP,
                        triggerAtMillis, pendingIntent);
            }
        }
    }

    private static Calendar getNextTriggerTime(Habit habit) {
        String[] parts = habit.getTime().split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long now = System.currentTimeMillis();
        for (int attempts = 0; attempts < 8; attempts++) {
            if (calendar.getTimeInMillis() > now && isDayEnabled(habit, calendar)) {
                return calendar;
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return null;
    }

    private static AlarmManager.AlarmClockInfo createAlarmClockInfo(
            Context context, int requestCode, long triggerAtMillis) {
        Intent showIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingShowIntent = PendingIntent.getActivity(context,
                requestCode, showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new AlarmManager.AlarmClockInfo(triggerAtMillis, pendingShowIntent);
    }

    private static boolean canUseExactAlarm(AlarmManager alarmManager) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
    }

    private static boolean hasEnabledDay(Habit habit) {
        return habit.getDays() != null && habit.getDays().containsValue(true);
    }

    private static int getRequestCode(Habit habit) {
        return (habit.getName() + "|" + habit.getTime()).hashCode();
    }

    private static boolean isDayEnabled(Habit habit, Calendar cal) {
        String[] keys = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        String key = keys[dayOfWeek - 1];
        return habit.getDays() != null && habit.getDays().getOrDefault(key, false);
    }
}
