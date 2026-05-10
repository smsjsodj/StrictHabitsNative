package com.stricthabits.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TelegramService extends Service {
    private Handler handler = new Handler();
    private Runnable pollRunnable;
    private SharedPreferences prefs;
    private String botToken = "8664151607:AAHHy9RDEN3gx2iNn_QNFM7Qhyw2dIIdm3Y"; // твой токен
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
                if (!chatId.isEmpty()) {
                    pollUpdates(chatId);
                }
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(pollRunnable);
    }

    private void pollUpdates(String myChatId) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=30");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject obj = new JSONObject(sb.toString());
            if (obj.getBoolean("ok")) {
                // разбор обновлений
                // допустим, упростим: проверяем /unlock
                String text = obj.toString().toLowerCase();
                if (text.contains("/unlock") && text.contains(myChatId)) {
                    // вызов разблокировки (заглушка)
                    Toast.makeText(this, "Unlock via Telegram", Toast.LENGTH_SHORT).show();
                }
            }
            reader.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}