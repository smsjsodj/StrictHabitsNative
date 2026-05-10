package com.stricthabits.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
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
    private WindowManager windowManager;
    private View overlayView;
    private Handler handler = new Handler();
    private Runnable soundRunnable;
    private MediaPlayer mediaPlayer;
    private String habitName, habitTime;
    private boolean soundEnabled;
    private boolean isUnlocked = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            habitName = intent.getStringExtra("habit_name");
            habitTime = intent.getStringExtra("habit_time");
            soundEnabled = intent.getBooleanExtra("sound_enabled", true);
        }
        showOverlay();
        if (soundEnabled) startSoundLoop();
        return START_STICKY;
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
            if (text.equalsIgnoreCase("я клянусь жопой")) {
                unlock();
            } else {
                Toast.makeText(LockService.this, "Неверная фраза", Toast.LENGTH_SHORT).show();
            }
        });

        int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        windowManager.addView(overlayView, params);
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
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(mp -> mp.release());
            } else {
                // вибрация как запасной вариант
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(500);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void stopSoundLoop() {
        if (soundRunnable != null) handler.removeCallbacks(soundRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
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
    public IBinder onBind(Intent intent) { return null; }
}