package com.stricthabits.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

public class HabitScheduler {
    public static void schedule(Context context, Habit habit) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("habit_name", habit.getName());
        intent.putExtra("habit_time", habit.getTime());
        intent.putExtra("telegram_only", habit.isTelegramOnly());
        intent.putExtra("sound_enabled", habit.isSoundEnabled());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                habit.getName().hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, habit.getHour());
        calendar.set(Calendar.MINUTE, habit.getMinute());
        calendar.set(Calendar.SECOND, 0);
        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pendingIntent);
    }
}