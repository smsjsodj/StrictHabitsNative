package com.stricthabits.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LockService extends Service {
    private static final String CHANNEL_ID = "HabitLockChannel";

    private WindowManager windowManager;
    private View overlayView;
    private final Handler handler = new Handler();
    private Runnable soundRunnable;
    private Ringtone ringtone;
    private String habitName;
    private String habitTime;
    private boolean soundEnabled;
    private boolean isUnlocked = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            habitName = intent.getStringExtra("habit_name");
            habitTime = intent.getStringExtra("habit_time");
            soundEnabled = intent.getBooleanExtra("sound_enabled", true);
        }

        createNotificationChannel();
        startForeground(1, createNotification());

        showOverlay();
        if (soundEnabled) startSoundLoop();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Habit Reminders", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Strict Habit")
                    .setContentText("\u041f\u043e\u0440\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c: " + habitName)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("Strict Habit")
                    .setContentText("\u041f\u043e\u0440\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c: " + habitName)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .build();
        }
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.lock_screen, null);

        TextView tvName = overlayView.findViewById(R.id.tvHabitName);
        TextView tvTime = overlayView.findViewById(R.id.tvTime);
        EditText etConfirm = overlayView.findViewById(R.id.etConfirm);
        Button btnUnlock = overlayView.findViewById(R.id.btnUnlock);

        tvName.setText(habitName);
        tvTime.setText(habitTime);

        btnUnlock.setOnClickListener(v -> {
            String text = etConfirm.getText().toString().trim();
            if (text.equalsIgnoreCase("\u044f \u043a\u043b\u044f\u043d\u0443\u0441\u044c \u0436\u043e\u043f\u043e\u0439")) {
                unlock();
            } else {
                Toast.makeText(LockService.this,
                        "\u041d\u0435\u0432\u0435\u0440\u043d\u0430\u044f \u0444\u0440\u0430\u0437\u0430",
                        Toast.LENGTH_SHORT).show();
            }
        });

        int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this,
                        "\u041f\u043e\u0440\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c: "
                                + habitName
                                + " (\u043d\u0435\u0442 \u043f\u0440\u0430\u0432 \u043d\u0430 \u043e\u0432\u0435\u0440\u043b\u0435\u0439)",
                        Toast.LENGTH_LONG).show();
            } else {
                windowManager.addView(overlayView, params);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this,
                    "\u041f\u043e\u0440\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c: " + habitName,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startSoundLoop() {
        soundRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUnlocked) {
                    playSound();
                    handler.postDelayed(this, 2000);
                }
            }
        };
        handler.post(soundRunnable);
    }

    private void playSound() {
        try {
            if (ringtone != null && ringtone.isPlaying()) return;

            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alert == null) {
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            ringtone = RingtoneManager.getRingtone(this, alert);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                ringtone.play();
            } else {
                vibrate();
            }
        } catch (Exception e) {
            vibrate();
        }
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
        }
    }

    private void stopSoundLoop() {
        if (soundRunnable != null) handler.removeCallbacks(soundRunnable);
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private void unlock() {
        isUnlocked = true;
        stopSoundLoop();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSoundLoop();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
