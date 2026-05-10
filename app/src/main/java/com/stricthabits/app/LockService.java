package com.stricthabits.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LockService extends Service {
    private WindowManager windowManager;
    private View lockView;
    private MediaPlayer mediaPlayer;
    private Habit currentHabit;
    private boolean isPlaying = false;
    private Handler soundHandler = new Handler();
    private Runnable soundRunnable;

    public static void triggerNow(Context context, Habit habit) {
        Intent intent = new Intent(context, LockService.class);
        intent.putExtra("habit", habit.name);
        intent.putExtra("time", habit.time);
        intent.putExtra("telegramOnly", habit.telegramOnly);
        intent.putExtra("sound", habit.sound);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("habit")) {
            currentHabit = new Habit(
                    intent.getStringExtra("habit"),
                    intent.getStringExtra("time"),
                    intent.getBooleanExtra("telegramOnly", false),
                    intent.getBooleanExtra("sound", true)
            );
            showLockScreen();
            if (currentHabit.sound) startLoopSound();
        }
        return START_NOT_STICKY;
    }

    private void showLockScreen() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);
        lockView = inflater.inflate(R.layout.lock_screen, null);

        TextView habitName = lockView.findViewById(R.id.lockHabitName);
        TextView habitTime = lockView.findViewById(R.id.lockHabitTime);
        EditText editPhrase = lockView.findViewById(R.id.lockConfirmPhrase);
        Button unlockBtn = lockView.findViewById(R.id.lockUnlockBtn);
        TextView telegramHint = lockView.findViewById(R.id.lockTelegramHint);

        habitName.setText(currentHabit.name);
        habitTime.setText(currentHabit.time);

        if (currentHabit.telegramOnly) {
            telegramHint.setVisibility(View.VISIBLE);
            telegramHint.setText("Разблокировка только через Telegram: отправьте /unlock боту");
            editPhrase.setVisibility(View.GONE);
        }

        unlockBtn.setOnClickListener(v -> {
            if (currentHabit.telegramOnly) {
                Toast.makeText(LockService.this, "Эта привычка требует Telegram разблокировки", Toast.LENGTH_LONG).show();
            } else {
                String phrase = editPhrase.getText().toString().trim();
                if (phrase.equalsIgnoreCase("Я клянусь жопой")) {
                    dismissLock();
                } else {
                    Toast.makeText(LockService.this, "Неверная фраза", Toast.LENGTH_SHORT).show();
                }
            }
        });

        int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                WindowManager.LayoutParams.FORMAT_CHANGED
        );
        windowManager.addView(lockView, params);
    }

    private void startLoopSound() {
        mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI);
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(1.0f, 1.0f);
            mediaPlayer.start();
            isPlaying = true;
        }
    }

    private void stopSound() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (soundHandler != null) soundHandler.removeCallbacks(soundRunnable);
    }

    private void dismissLock() {
        stopSound();
        if (lockView != null && windowManager != null) windowManager.removeView(lockView);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissLock();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}