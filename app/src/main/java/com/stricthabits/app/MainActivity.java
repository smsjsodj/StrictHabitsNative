package com.stricthabits.app;
import android.view.View;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

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
        adapter = new HabitAdapter(habitList,
                position -> deleteHabit(position),
                habit -> testHabit(habit));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddHabit).setOnClickListener(v -> showAddDialog());
        findViewById(R.id.btnTelegramSetup).setOnClickListener(v -> showTelegramDialog());
        findViewById(R.id.btnRequestOverlay).setOnClickListener(v -> requestOverlayPermission());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void showAddDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_habit, null);
        EditText etName = view.findViewById(R.id.habitName);
        Button btnTime = view.findViewById(R.id.btnSelectTime);
        SwitchCompat swTelegram = view.findViewById(R.id.switchTelegram);
        SwitchCompat swSound = view.findViewById(R.id.switchSound);

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
            picker.show(getSupportFragmentManager(), "time_picker");
        });

        builder.setTitle("Новая привычка")
                .setView(view)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String time = String.format("%02d:%02d", hour[0], minute[0]);
                    Habit habit = new Habit(name, time, swTelegram.isChecked(), swSound.isChecked());
                    habitList.add(habit);
                    saveHabits();
                    adapter.notifyItemInserted(habitList.size() - 1);
                    HabitScheduler.schedule(this, habit);
                })
                .show();
    }

    private void showTelegramDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View v = getLayoutInflater().inflate(R.layout.dialog_telegram, null);
        EditText etChatId = v.findViewById(R.id.chatId);
        etChatId.setText(prefs.getString("telegram_chat_id", ""));
        builder.setTitle("Telegram бот")
                .setView(v)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String chatId = etChatId.getText().toString();
                    prefs.edit().putString("telegram_chat_id", chatId).apply();
                    Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
                    startTelegramService();
                })
                .show();
    }

    private void startTelegramService() {
        Intent intent = new Intent(this, TelegramService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void deleteHabit(int position) {
        habitList.remove(position);
        saveHabits();
        adapter.notifyItemRemoved(position);
    }

    private void testHabit(Habit habit) {
        Intent intent = new Intent(this, LockService.class);
        intent.putExtra("habit_name", habit.getName());
        intent.putExtra("habit_time", habit.getTime());
        intent.putExtra("telegram_only", habit.isTelegramOnly());
        intent.putExtra("sound_enabled", habit.isSoundEnabled());
        startService(intent);
    }

    private void loadHabits() {
        try {
            String json = prefs.getString("list", "[]");
            JSONArray arr = new JSONArray(json);
            habitList.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Habit h = new Habit(obj.getString("name"), obj.getString("time"),
                        obj.getBoolean("telegramOnly"), obj.getBoolean("soundEnabled"));
                habitList.add(h);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveHabits() {
        try {
            JSONArray arr = new JSONArray();
            for (Habit h : habitList) {
                JSONObject obj = new JSONObject();
                obj.put("name", h.getName());
                obj.put("time", h.getTime());
                obj.put("telegramOnly", h.isTelegramOnly());
                obj.put("soundEnabled", h.isSoundEnabled());
                arr.put(obj);
            }
            prefs.edit().putString("list", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}