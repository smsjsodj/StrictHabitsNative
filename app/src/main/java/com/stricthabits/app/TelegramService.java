package com.stricthabits.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TelegramService extends Service {
    private final Handler handler = new Handler();
    private Runnable pollRunnable;
    private SharedPreferences prefs;
    private int lastUpdateId = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("habits", MODE_PRIVATE);
        startPolling();
    }

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                String chatId = prefs.getString("telegram_chat_id", "");
                String botToken = prefs.getString("telegram_bot_token", "");
                if (!chatId.isEmpty() && !botToken.isEmpty()) {
                    pollUpdates(botToken, chatId);
                }
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(pollRunnable);
    }

    private void pollUpdates(String botToken, String myChatId) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.telegram.org/bot" + botToken
                        + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=0");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject obj = new JSONObject(sb.toString());
                if (obj.optBoolean("ok", false)) {
                    String text = obj.toString().toLowerCase();
                    if (text.contains("/unlock") && text.contains(myChatId)) {
                        sendBroadcast(new Intent("com.stricthabits.app.TELEGRAM_UNLOCK"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
