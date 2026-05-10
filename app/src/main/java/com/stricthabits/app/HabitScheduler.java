package com.stricthabits.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;
import java.util.Locale;

public class HabitScheduler {
    public static void schedule(Context context, Habit habit) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("habit_name", habit.getName());
        intent.putExtra("habit_time", habit.getTime());
        intent.putExtra("sound_enabled", habit.isSoundEnabled());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                habit.getName().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String[] parts = habit.getTime().split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Ищем ближайший активный день
        int attempts = 0;
        while (attempts < 8) {
            if (calendar.getTimeInMillis() > System.currentTimeMillis() && isDayEnabled(habit, calendar)) {
                break;
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            attempts++;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(
                        calendar.getTimeInMillis(), pendingIntent);
                alarmManager.setAlarmClock(info, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(), pendingIntent);
            }
        } catch (SecurityException e) {
            alarmManager.set(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(), pendingIntent);
        }
    }

    private static boolean isDayEnabled(Habit habit, Calendar cal) {
        String[] keys = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1 = Sunday, 2 = Monday...
        String key = keys[dayOfWeek - 1];
        return habit.getDays() != null && habit.getDays().getOrDefault(key, false);
    }
}