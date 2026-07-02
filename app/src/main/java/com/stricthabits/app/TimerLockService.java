package com.stricthabits.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class TimerLockService extends Service {
    private static final String TAG = "TimerLockService";
    private static final String CHANNEL_ID = "TimerLockChannel";
    private WindowManager windowManager;
    private View overlayView;
    private TextView tvName;
    private TextView tvCountdown;
    private Handler handler = new Handler();
    private long endTimeMillis;
    private String lockName;
    private boolean overlayAdded = false;

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long remainingMillis = endTimeMillis - System.currentTimeMillis();

            if (remainingMillis <= 0) {
                unlockTimer();
            } else {
                updateTimerDisplay(remainingMillis);
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            lockName = intent.getStringExtra("lock_name");
            int durationMinutes = intent.getIntExtra("duration_minutes", 20);
            endTimeMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000);
        }

        createNotificationChannel();
        startForeground(2, createNotification());
        showTimerOverlay();
        handler.post(timerRunnable);

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Timer Lock",
                    NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("Таймер-блокировка")
                .setContentText(lockName != null ? lockName : "Активна")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .build();
    }

    private void showTimerOverlay() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Нужно разрешение на отображение поверх окон", Toast.LENGTH_LONG).show();
                stopSelf();
                return;
            }

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = LayoutInflater.from(this);
            overlayView = inflater.inflate(R.layout.timer_lock_screen, null);

            tvName = overlayView.findViewById(R.id.tvTimerLockName);
            tvCountdown = overlayView.findViewById(R.id.tvTimerCountdown);

            if (lockName != null && !lockName.isEmpty()) {
                tvName.setText(lockName);
            }

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP;

            windowManager.addView(overlayView, params);
            overlayAdded = true;
            Log.d(TAG, "Timer overlay shown");

        } catch (Exception e) {
            Log.e(TAG, "Error showing timer overlay", e);
            stopSelf();
        }
    }

    private void updateTimerDisplay(long remainingMillis) {
        if (tvCountdown == null) return;

        int totalSeconds = (int) (remainingMillis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        String timeText = String.format("%02d:%02d", minutes, seconds);
        tvCountdown.setText(timeText);
    }

    private void unlockTimer() {
        handler.removeCallbacks(timerRunnable);
        removeOverlay();
        Toast.makeText(this, "Таймер завершен!", Toast.LENGTH_LONG).show();
        stopSelf();
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null && overlayAdded) {
            try {
                windowManager.removeView(overlayView);
                overlayAdded = false;
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            }
        }
        overlayView = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
        removeOverlay();
        Log.d(TAG, "TimerLockService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
