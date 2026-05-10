package com.stricthabits.app;

import android.content.Context;
import androidx.work.*;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class HabitScheduler {
    public static void schedule(Context context, Habit habit) {
        String[] parts = habit.time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        long delay = target.getTimeInMillis() - now.getTimeInMillis();
        if (delay < 0) delay += 24 * 60 * 60 * 1000;

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(LockWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(habit.name)
                .build();
        WorkManager.getInstance(context).enqueue(work);
    }

    public static class LockWorker extends Worker {
        public LockWorker(Context context, WorkerParameters params) { super(context, params); }
        @Override
        public Result doWork() {
            // Здесь нужно получить привычку и вызвать LockService.triggerNow
            return Result.success();
        }
    }
}