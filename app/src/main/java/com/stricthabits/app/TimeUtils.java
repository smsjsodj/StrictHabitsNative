package com.stricthabits.app;

import java.util.Calendar;
import java.util.Locale;

final class TimeUtils {
    private TimeUtils() {
    }

    static int[] parseTimeParts(String time, int defaultHour, int defaultMinute) {
        int safeDefaultHour = clamp(defaultHour, 0, 23);
        int safeDefaultMinute = clamp(defaultMinute, 0, 59);
        if (time == null) {
            return new int[]{safeDefaultHour, safeDefaultMinute};
        }

        String[] parts = time.trim().split(":");
        if (parts.length != 2) {
            return new int[]{safeDefaultHour, safeDefaultMinute};
        }

        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return new int[]{safeDefaultHour, safeDefaultMinute};
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            return new int[]{safeDefaultHour, safeDefaultMinute};
        }
    }

    static int toMinutes(String time, int fallbackMinutes) {
        if (time == null) {
            return fallbackMinutes;
        }

        String[] parts = time.trim().split(":");
        if (parts.length != 2) {
            return fallbackMinutes;
        }

        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return fallbackMinutes;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return fallbackMinutes;
        }
    }

    static String format(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                clamp(hour, 0, 23),
                clamp(minute, 0, 59));
    }

    static int currentMinutes() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
