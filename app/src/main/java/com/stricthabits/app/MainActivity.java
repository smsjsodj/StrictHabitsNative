package com.stricthabits.app;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private HabitAdapter adapter;
    private final List<Habit> habitList = new ArrayList<>();
    private SharedPreferences prefs;
    private final BroadcastReceiver habitsUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshHabits();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("habits", MODE_PRIVATE);
        loadHabits();
        scheduleAllHabits();

        recyclerView = findViewById(R.id.habitsRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HabitAdapter(habitList,
                this::deleteHabit,
                this::showEditDialog,
                this::skipToday,
                this::toggleHabit,
                this::setCompleted);
        recyclerView.setAdapter(adapter);
        registerHabitsUpdatedReceiver();

        findViewById(R.id.btnFocusLock).setOnClickListener(v -> startFocusLock());
        findViewById(R.id.btnTelegram).setOnClickListener(v -> showTelegramDialog());
        findViewById(R.id.btnAddHabit).setOnClickListener(v -> showHabitDialog(-1));

        View btnOverlay = findViewById(R.id.btnRequestOverlay);
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());
        updateOverlayButton();
        requestExactAlarmPermission();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOverlayButton();
        if (adapter != null) {
            refreshHabits();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(habitsUpdatedReceiver);
    }

    private void startFocusLock() {
        if (!hasTelegramSettings()) {
            Toast.makeText(this, "Сначала сохрани Telegram token и chat id", Toast.LENGTH_LONG).show();
            showTelegramDialog();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Для фокус-блокировки нужно разрешение поверх окон", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }

        Intent intent = new Intent(this, LockService.class);
        intent.putExtra("habit_name", "Фокус");
        intent.putExtra("habit_time", "Жду /unlock в Telegram");
        intent.putExtra("sound_enabled", false);
        intent.putExtra(LockService.EXTRA_LOCK_KIND, LockService.LOCK_KIND_FOCUS);
        intent.putExtra(LockService.EXTRA_UNLOCK_MODE, LockService.UNLOCK_MODE_TELEGRAM);
        startLockService(intent);
    }

    private void showTelegramDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_telegram, null);
        EditText etToken = view.findViewById(R.id.botToken);
        EditText etChatId = view.findViewById(R.id.chatId);
        etToken.setText(prefs.getString("telegram_bot_token", ""));
        etChatId.setText(prefs.getString("telegram_chat_id", ""));

        new AlertDialog.Builder(this)
                .setTitle("Telegram")
                .setView(view)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    prefs.edit()
                            .putString("telegram_bot_token", etToken.getText().toString().trim())
                            .putString("telegram_chat_id", etChatId.getText().toString().trim())
                            .apply();
                    Toast.makeText(this, "Telegram сохранен", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showEditDialog(int position) {
        showHabitDialog(position);
    }

    private void showHabitDialog(int position) {
        boolean editing = position >= 0;
        Habit existing = editing ? habitList.get(position) : null;

        View view = getLayoutInflater().inflate(R.layout.dialog_add_habit, null);
        EditText etName = view.findViewById(R.id.habitName);
        Button btnTime = view.findViewById(R.id.btnSelectTime);
        CheckBox chkSound = view.findViewById(R.id.chkSound);
        CheckBox chkMon = view.findViewById(R.id.chkMon);
        CheckBox chkTue = view.findViewById(R.id.chkTue);
        CheckBox chkWed = view.findViewById(R.id.chkWed);
        CheckBox chkThu = view.findViewById(R.id.chkThu);
        CheckBox chkFri = view.findViewById(R.id.chkFri);
        CheckBox chkSat = view.findViewById(R.id.chkSat);
        CheckBox chkSun = view.findViewById(R.id.chkSun);

        int[] hour = {12};
        int[] minute = {0};
        if (existing != null) {
            etName.setText(existing.getName());
            chkSound.setChecked(existing.isSoundEnabled());
            setDayChecks(existing.getDays(), chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun);
            String[] parts = existing.getTime().split(":");
            hour[0] = Integer.parseInt(parts[0]);
            minute[0] = Integer.parseInt(parts[1]);
        }
        btnTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hour[0], minute[0]));
        btnTime.setOnClickListener(v -> new TimePickerDialog(this,
                (view1, hourOfDay, minuteOfHour) -> {
                    hour[0] = hourOfDay;
                    minute[0] = minuteOfHour;
                    btnTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hour[0], minute[0]));
                }, hour[0], minute[0], true).show());

        new AlertDialog.Builder(this)
                .setTitle(editing ? "Изменить привычку" : "Новая привычка")
                .setView(view)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Введи название", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String time = String.format(Locale.getDefault(), "%02d:%02d", hour[0], minute[0]);
                    Map<String, Boolean> days = collectDays(chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun);
                    Habit habit = new Habit(name, time, chkSound.isChecked(), days);

                    if (editing) {
                        Habit old = habitList.get(position);
                        HabitScheduler.cancel(this, old);
                        habit.setEnabled(old.isEnabled());
                        habit.setLastCompletedDate(old.getLastCompletedDate());
                        habit.setCompletedCount(old.getCompletedCount());
                        habit.setSkippedDate(old.getSkippedDate());
                        habitList.set(position, habit);
                        adapter.notifyItemChanged(position);
                    } else {
                        habitList.add(habit);
                        adapter.notifyItemInserted(habitList.size() - 1);
                    }
                    saveHabits();
                    HabitScheduler.schedule(this, habit);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteHabit(int position) {
        Habit habit = habitList.remove(position);
        HabitScheduler.cancel(this, habit);
        saveHabits();
        adapter.notifyItemRemoved(position);
    }

    private void skipToday(int position) {
        Habit habit = habitList.get(position);
        habit.setSkippedDate(today());
        HabitScheduler.cancel(this, habit);
        saveHabits();
        adapter.notifyItemChanged(position);
        Toast.makeText(this, "Сегодня пропущено", Toast.LENGTH_SHORT).show();
    }

    private void toggleHabit(int position) {
        Habit habit = habitList.get(position);
        habit.setEnabled(!habit.isEnabled());
        if (habit.isEnabled()) {
            habit.setSkippedDate("");
            HabitScheduler.schedule(this, habit);
        } else {
            HabitScheduler.cancel(this, habit);
        }
        saveHabits();
        if (completed) {
            HabitScheduler.cancel(this, habit);
            if (hasSelectedDays(habit)) {
                HabitScheduler.scheduleNext(this, habit);
            }
        } else if (habit.isEnabled()) {
            HabitScheduler.schedule(this, habit);
        }
        adapter.notifyItemChanged(position);
    }

    private void setCompleted(int position, boolean completed) {
        Habit habit = habitList.get(position);
        boolean wasCompletedToday = today().equals(habit.getLastCompletedDate());
        habit.setCompletedToday(completed);
        habit.setLastCompletedDate(completed ? today() : "");
        if (completed && !wasCompletedToday) {
            habit.setCompletedCount(habit.getCompletedCount() + 1);
        } else if (!completed && wasCompletedToday) {
            habit.setCompletedCount(habit.getCompletedCount() - 1);
        }
        saveHabits();
        adapter.notifyItemChanged(position);
        Toast.makeText(this, completed ? "Выполнено" : "Снято выполнение", Toast.LENGTH_SHORT).show();
    }

    private void loadHabits() {
        try {
            String json = prefs.getString("list", "[]");
            JSONArray arr = new JSONArray(json);
            habitList.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Map<String, Boolean> days = new HashMap<>();
                JSONObject daysObj = obj.optJSONObject("days");
                if (daysObj != null) {
                    days.put("mon", daysObj.optBoolean("mon", false));
                    days.put("tue", daysObj.optBoolean("tue", false));
                    days.put("wed", daysObj.optBoolean("wed", false));
                    days.put("thu", daysObj.optBoolean("thu", false));
                    days.put("fri", daysObj.optBoolean("fri", false));
                    days.put("sat", daysObj.optBoolean("sat", false));
                    days.put("sun", daysObj.optBoolean("sun", false));
                }

                Habit habit = new Habit(
                        obj.getString("name"),
                        obj.getString("time"),
                        obj.optBoolean("soundEnabled", true),
                        days);
                String lastDate = obj.optString("lastCompletedDate", "");
                habit.setCompletedToday(today().equals(lastDate));
                habit.setLastCompletedDate(lastDate);
                habit.setCompletedCount(obj.optInt("completedCount", 0));
                habit.setEnabled(obj.optBoolean("enabled", true));
                String skippedDate = obj.optString("skippedDate", "");
                habit.setSkippedDate(today().equals(skippedDate) ? skippedDate : "");
                habitList.add(habit);
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
                obj.put("soundEnabled", h.isSoundEnabled());
                obj.put("lastCompletedDate", h.getLastCompletedDate());
                obj.put("completedCount", h.getCompletedCount());
                obj.put("skippedDate", h.getSkippedDate());
                obj.put("enabled", h.isEnabled());
                JSONObject daysObj = new JSONObject();
                for (Map.Entry<String, Boolean> e : h.getDays().entrySet()) {
                    daysObj.put(e.getKey(), e.getValue());
                }
                obj.put("days", daysObj);
                arr.put(obj);
            }
            prefs.edit().putString("list", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void scheduleAllHabits() {
        for (Habit h : habitList) {
            HabitScheduler.schedule(this, h);
        }
    }

    private Map<String, Boolean> collectDays(CheckBox mon, CheckBox tue, CheckBox wed, CheckBox thu,
                                             CheckBox fri, CheckBox sat, CheckBox sun) {
        Map<String, Boolean> days = new HashMap<>();
        days.put("mon", mon.isChecked());
        days.put("tue", tue.isChecked());
        days.put("wed", wed.isChecked());
        days.put("thu", thu.isChecked());
        days.put("fri", fri.isChecked());
        days.put("sat", sat.isChecked());
        days.put("sun", sun.isChecked());
        return days;
    }

    private void setDayChecks(Map<String, Boolean> days, CheckBox mon, CheckBox tue, CheckBox wed,
                              CheckBox thu, CheckBox fri, CheckBox sat, CheckBox sun) {
        if (days == null) return;
        mon.setChecked(days.getOrDefault("mon", false));
        tue.setChecked(days.getOrDefault("tue", false));
        wed.setChecked(days.getOrDefault("wed", false));
        thu.setChecked(days.getOrDefault("thu", false));
        fri.setChecked(days.getOrDefault("fri", false));
        sat.setChecked(days.getOrDefault("sat", false));
        sun.setChecked(days.getOrDefault("sun", false));
    }

    private void startLockService(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private boolean hasTelegramSettings() {
        return !prefs.getString("telegram_bot_token", "").isEmpty()
                && !prefs.getString("telegram_chat_id", "").isEmpty();
    }

    private boolean hasSelectedDays(Habit habit) {
        return habit.getDays() != null && habit.getDays().containsValue(true);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void updateOverlayButton() {
        View btnOverlay = findViewById(R.id.btnRequestOverlay);
        if (btnOverlay == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            btnOverlay.setVisibility(View.VISIBLE);
        } else {
            btnOverlay.setVisibility(View.GONE);
        }
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void refreshHabits() {
        loadHabits();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void registerHabitsUpdatedReceiver() {
        IntentFilter filter = new IntentFilter(LockService.ACTION_HABITS_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(habitsUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(habitsUpdatedReceiver, filter);
        }
    }
}
