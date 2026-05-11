package com.stricthabits.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LockService extends Service {
    private static final String CHANNEL_ID = "HabitLockChannel";
    public static final String EXTRA_UNLOCK_MODE = "unlock_mode";
    public static final String EXTRA_LOCK_KIND = "lock_kind";
    public static final String LOCK_KIND_HABIT = "habit";
    public static final String LOCK_KIND_FOCUS = "focus";
    public static final String UNLOCK_MODE_PHRASE = "phrase";
    public static final String UNLOCK_MODE_TELEGRAM = "telegram";

    private WindowManager windowManager;
    private View overlayView;
    private final Handler handler = new Handler();
    private Runnable soundRunnable;
    private Runnable telegramRunnable;
    private Ringtone ringtone;
    private String habitName;
    private String habitTime;
    private String lockKind = LOCK_KIND_HABIT;
    private String unlockMode = UNLOCK_MODE_PHRASE;
    private boolean soundEnabled;
    private boolean isUnlocked = false;
    private boolean telegramReadyForUnlock = false;
    private boolean overlayAdded = false;
    private int lastTelegramUpdateId = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isLockActive()) {
            handleIncomingWhileLocked(intent);
            return START_STICKY;
        }

        if (intent != null) {
            habitName = intent.getStringExtra("habit_name");
            habitTime = intent.getStringExtra("habit_time");
            lockKind = intent.getStringExtra(EXTRA_LOCK_KIND);
            if (lockKind == null) lockKind = LOCK_KIND_HABIT;
            unlockMode = intent.getStringExtra(EXTRA_UNLOCK_MODE);
            if (unlockMode == null) unlockMode = UNLOCK_MODE_PHRASE;
            soundEnabled = intent.getBooleanExtra("sound_enabled", true);
        }
        isUnlocked = false;
        telegramReadyForUnlock = false;

        createNotificationChannel();
        startForeground(1, createNotification());

        showOverlay();
        if (soundEnabled) startSoundLoop();
        if (UNLOCK_MODE_TELEGRAM.equals(unlockMode)) startTelegramUnlockPolling();
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
                    .setContentText(getNotificationText())
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setOngoing(true)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("Strict Habit")
                    .setContentText(getNotificationText())
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

        if (UNLOCK_MODE_TELEGRAM.equals(unlockMode)) {
            tvTime.setText("Telegram: /unlock");
            etConfirm.setVisibility(View.GONE);
            btnUnlock.setVisibility(View.GONE);
        }

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
                overlayAdded = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this,
                    "\u041f\u043e\u0440\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c: " + habitName,
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isLockActive() {
        return overlayView != null && !isUnlocked;
    }

    private void handleIncomingWhileLocked(Intent intent) {
        if (intent == null) return;

        String incomingKind = intent.getStringExtra(EXTRA_LOCK_KIND);
        if (incomingKind == null) incomingKind = LOCK_KIND_HABIT;

        if (LOCK_KIND_HABIT.equals(incomingKind)) {
            String incomingHabitName = intent.getStringExtra("habit_name");
            if (incomingHabitName != null) {
                markHabitCompleted(incomingHabitName);
            }
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

    private void startTelegramUnlockPolling() {
        telegramRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUnlocked) {
                    pollTelegramUnlock(telegramReadyForUnlock);
                    telegramReadyForUnlock = true;
                    handler.postDelayed(this, 3000);
                }
            }
        };
        handler.post(telegramRunnable);
    }

    private void pollTelegramUnlock(boolean allowUnlock) {
        SharedPreferences prefs = getSharedPreferences("habits", MODE_PRIVATE);
        String botToken = prefs.getString("telegram_bot_token", "");
        String chatId = prefs.getString("telegram_chat_id", "");
        if (botToken.isEmpty() || chatId.isEmpty()) {
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String urlText = "https://api.telegram.org/bot" + botToken
                        + "/getUpdates?offset=" + (lastTelegramUpdateId + 1) + "&timeout=0";
                URL url = new URL(urlText);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject response = new JSONObject(sb.toString());
                if (!response.optBoolean("ok", false)) return;

                JSONArray updates = response.optJSONArray("result");
                if (updates == null) return;

                for (int i = 0; i < updates.length(); i++) {
                    JSONObject update = updates.getJSONObject(i);
                    lastTelegramUpdateId = Math.max(lastTelegramUpdateId, update.optInt("update_id", 0));
                    JSONObject message = update.optJSONObject("message");
                    if (message == null) continue;

                    JSONObject chat = message.optJSONObject("chat");
                    String text = message.optString("text", "").trim();
                    if (chat != null
                            && chatId.equals(String.valueOf(chat.optLong("id")))
                            && "/unlock".equalsIgnoreCase(text)
                            && allowUnlock) {
                        handler.post(() -> unlock());
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
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
        if (telegramRunnable != null) handler.removeCallbacks(telegramRunnable);
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private String getNotificationText() {
        if (UNLOCK_MODE_TELEGRAM.equals(unlockMode)) {
            return "\u0424\u043e\u043a\u0443\u0441-\u0440\u0435\u0436\u0438\u043c. \u0416\u0434\u0443 /unlock \u0432 Telegram";
        }
        return "\u041f\u043e\u0440\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c: " + habitName;
    }

    private void unlock() {
        isUnlocked = true;
        if (LOCK_KIND_HABIT.equals(lockKind)) {
            markHabitCompleted(habitName);
        }
        stopSoundLoop();
        removeOverlay();
        stopSelf();
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null && overlayAdded) {
            windowManager.removeView(overlayView);
        }
        overlayAdded = false;
        overlayView = null;
    }

    private void markHabitCompleted(String name) {
        if (name == null || name.isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences("habits", MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString("list", "[]"));
            String today = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());

            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (name.equals(obj.optString("name", ""))) {
                    obj.put("lastCompletedDate", today);
                    obj.put("skippedDate", "");
                    changed = true;
                    break;
                }
            }

            if (changed) {
                prefs.edit().putString("list", arr.toString()).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSoundLoop();
        removeOverlay();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
