package com.stricthabits.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import okhttp3.*;
import org.json.*;
import java.io.IOException;

public class TelegramService extends Service {
    private static final String BOT_TOKEN = "YOUR_BOT_TOKEN"; // замените на свой
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private int lastUpdateId = 0;
    private OkHttpClient client = new OkHttpClient();

    @Override
    public void onCreate() {
        super.onCreate();
        startPolling();
    }

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=30";
                Request request = new Request.Builder().url(url).build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) { e.printStackTrace(); }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            String json = response.body().string();
                            JSONObject obj = new JSONObject(json);
                            if (obj.getBoolean("ok")) {
                                JSONArray result = obj.getJSONArray("result");
                                for (int i=0; i<result.length(); i++) {
                                    JSONObject update = result.getJSONObject(i);
                                    lastUpdateId = update.getInt("update_id");
                                    JSONObject message = update.optJSONObject("message");
                                    if (message != null) {
                                        String text = message.optString("text");
                                        String chatId = message.getJSONObject("chat").getString("id");
                                        if ("/unlock".equals(text)) {
                                            // TODO: проверить, что chatId совпадает с сохранённым в настройках
                                            unlockWithTelegram();
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                        handler.postDelayed(this, 2000);
                    }
                });
            }
        };
        handler.post(pollRunnable);
    }

    private void unlockWithTelegram() {
        // Заглушка: завершить блокировку через LockService
        // Для простоты просто уведомим
        handler.post(() -> Toast.makeText(TelegramService.this, "Получена команда /unlock", Toast.LENGTH_SHORT).show());
        // Здесь нужно вызвать метод dismissLock из LockService
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}