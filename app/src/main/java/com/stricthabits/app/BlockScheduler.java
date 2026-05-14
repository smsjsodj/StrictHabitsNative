package com.stricthabits.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public class BlockScheduler {
    public static void cancel(Context context, BlockPeriod block) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || block == null) return;

        Intent startIntent = new Intent(context, AlarmReceiver.class);
        PendingIntent startPI = PendingIntent.getBroadcast(context,
                getRequestCodeStart(block), startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent endIntent = new Intent(context, AlarmReceiver.class);
        PendingIntent endPI = PendingIntent.getBroadcast(context,
                getRequestCodeEnd(block), endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(startPI);
        alarmManager.cancel(endPI);
    }

    public static void schedule(Context context, BlockPeriod block) {
        schedule(context, block, true);
    }

    public static void scheduleNext(Context context, BlockPeriod block) {
        schedule(context, block, false);
    }

    private static void schedule(Context context, BlockPeriod block, boolean allowCurrentMinute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || block == null) return;

        if (!block.isEnabled()) {
            cancel(context, block);
            return;
        }

        Calendar startCal = getNextStartTime(block, allowCurrentMinute);
        if (startCal == null) {
            cancel(context, block);
            return;
        }

        // compute end time for this instance
        String[] endParts = block.getEndTime().split(":");
        int endHour = Integer.parseInt(endParts[0]);
        int endMinute = Integer.parseInt(endParts[1]);
        Calendar endCal = (Calendar) startCal.clone();
        endCal.set(Calendar.HOUR_OF_DAY, endHour);
        endCal.set(Calendar.MINUTE, endMinute);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);

        // if end is before or equal start, it belongs to next day
        if (endCal.getTimeInMillis() <= startCal.getTimeInMillis()) {
            endCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long startMillis = startCal.getTimeInMillis();
        long endMillis = endCal.getTimeInMillis();

        // Prepare intents
        Intent startIntent = new Intent(context, AlarmReceiver.class);
        startIntent.putExtra("block_start", true);
        startIntent.putExtra("block_start_time", block.getStartTime());
        startIntent.putExtra("block_end_time", block.getEndTime());

        Intent endIntent = new Intent(context, AlarmReceiver.class);
        endIntent.putExtra("block_end", true);
        endIntent.putExtra("block_start_time", block.getStartTime());
        endIntent.putExtra("block_end_time", block.getEndTime());

        PendingIntent startPI = PendingIntent.getBroadcast(context,
                getRequestCodeStart(block), startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent endPI = PendingIntent.getBroadcast(context,
                getRequestCodeEnd(block), endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(startPI);
        alarmManager.cancel(endPI);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canUseExactAlarm(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMillis, startPI);
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, endPI);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, startMillis, startPI);
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, endMillis, endPI);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, startMillis, startPI);
                alarmManager.set(AlarmManager.RTC_WAKEUP, endMillis, endPI);
            }
        } catch (SecurityException e) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, startMillis, startPI);
            alarmManager.set(AlarmManager.RTC_WAKEUP, endMillis, endPI);
        }
    }

    private static boolean canUseExactAlarm(AlarmManager alarmManager) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
    }

    private static Calendar getNextStartTime(BlockPeriod block, boolean allowCurrentMinute) {
        try {
            String[] parts = block.getStartTime().split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            Calendar now = Calendar.getInstance();
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            long nowMs = System.currentTimeMillis();
            if (allowCurrentMinute && isSameMinute(cal, now)) {
                cal.setTimeInMillis(nowMs + 1000);
                return cal;
            }

            for (int i = 0; i < 8; i++) {
                if (cal.getTimeInMillis() > nowMs && isDayEnabled(block, cal)) {
                    return cal;
                }
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private static boolean isSameMinute(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
                && first.get(Calendar.HOUR_OF_DAY) == second.get(Calendar.HOUR_OF_DAY)
                && first.get(Calendar.MINUTE) == second.get(Calendar.MINUTE);
    }

    private static boolean isDayEnabled(BlockPeriod block, Calendar cal) {
        String[] keys = {"sun","mon","tue","wed","thu","fri","sat"};
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        String key = keys[dayOfWeek - 1];
        return block.getDays().getOrDefault(key, false);
    }

    private static int getRequestCodeStart(BlockPeriod block) {
        return ("block_start|" + block.getStartTime() + "|" + block.getEndTime()).hashCode();
    }

    private static int getRequestCodeEnd(BlockPeriod block) {
        return ("block_end|" + block.getStartTime() + "|" + block.getEndTime()).hashCode();
    }
}
