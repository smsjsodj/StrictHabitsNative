package com.stricthabits.app;
import android.os.Build;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
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
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private Runnable soundRunnable;
    private String habitName;
    private String habitTime;
    private boolean telegramOnly;
    private boolean soundEnabled;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            habitName = intent.getStringExtra("habit_name");
            habitTime = intent.getStringExtra("habit_time");
            telegramOnly = intent.getBooleanExtra("telegram_only", false);
            soundEnabled = intent.getBooleanExtra("sound_enabled", true);
        } else {
            habitName = "Привычка";
            habitTime = "Время";
        }

        showOverlay();
        if (soundEnabled) {
            startSoundLoop();
        }
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
                stopSoundLoop();
                windowManager.removeView(overlayView);
                stopSelf();
            } else {
                Toast.makeText(this, "Неверная фраза", Toast.LENGTH_SHORT).show();
            }
        });

        int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);
    }

private void startSoundLoop() {
    soundRunnable = new Runnable() {
        @Override
        public void run() {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(500);
            handler.postDelayed(this, 2000);
        }
    };
    handler.post(soundRunnable);
}

    private void stopSoundLoop() {
        if (soundRunnable != null) {
            handler.removeCallbacks(soundRunnable);
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
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