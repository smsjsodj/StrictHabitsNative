package com.stricthabits.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import org.json.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HabitAdapter adapter;
    private List<Habit> habitList = new ArrayList<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("habits", MODE_PRIVATE);
        loadHabits();

        recyclerView = findViewById(R.id.habitsRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HabitAdapter(habitList, position -> {
            habitList.remove(position);
            saveHabits();
            adapter.notifyItemRemoved(position);
        }, habit -> {
            LockService.triggerNow(MainActivity.this, habit);
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddHabit).setOnClickListener(v -> showAddHabitDialog());
        findViewById(R.id.btnTelegramSetup).setOnClickListener(v -> showTelegramSetup());
        findViewById(R.id.btnRequestOverlay).setOnClickListener(v -> requestOverlayPermission());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        }
        startLockService();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void startLockService() {
        Intent intent = new Intent(this, LockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void showAddHabitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_habit, null);
        EditText editName = view.findViewById(R.id.habitName);
        Button btnTime = view.findViewById(R.id.btnSelectTime);
        CheckBox chkTelegram = view.findViewById(R.id.chkTelegramOnly);
        CheckBox chkSound = view.findViewById(R.id.chkSound);

        int[] hour = {12}, minute = {0};
        btnTime.setText("12:00");
        btnTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(hour[0])
                    .setMinute(minute[0])
                    .build();
            picker.addOnPositiveButtonClickListener(dialog -> {
                hour[0] = picker.getHour();
                minute[0] = picker.getMinute();
                btnTime.setText(String.format("%02d:%02d", hour[0], minute[0]));
            });
            picker.show(getSupportFragmentManager(), "time");
        });

        builder.setTitle("Новая привычка")
                .setView(view)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = editName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    String time = String.format("%02d:%02d", hour[0], minute[0]);
                    Habit habit = new Habit(name, time, chkTelegram.isChecked(), chkSound.isChecked());
                    habitList.add(habit);
                    saveHabits();
                    adapter.notifyItemInserted(habitList.size()-1);
                    HabitScheduler.schedule(this, habit);
                })
                .show();
    }

    private void showTelegramSetup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View v = getLayoutInflater().inflate(R.layout.dialog_telegram, null);
        EditText chatIdInput = v.findViewById(R.id.chatId);
        chatIdInput.setText(prefs.getString("telegram_chat_id", ""));
        builder.setTitle("Telegram бот")
                .setView(v)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String chatId = chatIdInput.getText().toString();
                    prefs.edit().putString("telegram_chat_id", chatId).apply();
                    Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
                    startTelegramService();
                })
                .show();
    }

    private void startTelegramService() {
        Intent intent = new Intent(this, TelegramService.class);
        startService(intent);
    }

    private void loadHabits() {
        String json = prefs.getString("habits_json", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i=0; i<array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Habit h = new Habit(obj.getString("name"), obj.getString("time"),
                        obj.getBoolean("telegramOnly"), obj.getBoolean("sound"));
                habitList.add(h);
            }
        } catch (JSONException e) { e.printStackTrace(); }
    }

    private void saveHabits() {
        JSONArray array = new JSONArray();
        for (Habit h : habitList) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", h.name);
                obj.put("time", h.time);
                obj.put("telegramOnly", h.telegramOnly);
                obj.put("sound", h.soundEnabled);
                array.put(obj);
            } catch (JSONException e) {}
        }
        prefs.edit().putString("habits_json", array.toString()).apply();
    }
}