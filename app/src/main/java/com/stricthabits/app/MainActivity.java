package com.stricthabits.app;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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
    private final List<BlockPeriod> blockList = new ArrayList<>();
    private final List<TimerBlock> timerBlockList = new ArrayList<>();
    private final List<BlockedApp> blockedAppList = new ArrayList<>();
    private final List<WhitelistedApp> whitelistedAppList = new ArrayList<>();
    private SyncManager syncManager;
    private boolean habitsReceiverRegistered = false;
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

        try {
            syncManager = new SyncManager(this);
        } catch (Exception e) {
            Log.e("MainActivity", "Error initializing sync", e);
            syncManager = null;
        }

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
        findViewById(R.id.btnManageBlocks).setOnClickListener(v -> showBlocksDialog());

        Button btnBlockedApps = findViewById(R.id.btnBlockedApps);
        if (btnBlockedApps != null) {
            btnBlockedApps.setOnClickListener(v -> showBlockedAppsDialog());
        }

        Button btnWhitelistedApps = findViewById(R.id.btnWhitelistedApps);
        if (btnWhitelistedApps != null) {
            btnWhitelistedApps.setOnClickListener(v -> showWhitelistedAppsDialog());
        }

        Button btnTimerLock = findViewById(R.id.btnTimerLock);
        if (btnTimerLock != null) {
            btnTimerLock.setOnClickListener(v -> showTimerBlocksDialog());
        }

        Button btnSync = findViewById(R.id.btnSync);
        if (btnSync != null) {
            btnSync.setOnClickListener(v -> syncWithDesktop());
        }

        View btnOverlay = findViewById(R.id.btnRequestOverlay);
        if (btnOverlay != null) {
            btnOverlay.setOnClickListener(v -> requestOverlayPermission());
        }
        updateOverlayButton();
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
        if (habitsReceiverRegistered) {
            try {
                unregisterReceiver(habitsUpdatedReceiver);
            } catch (Exception e) {
                Log.w("MainActivity", "Receiver was already unregistered", e);
            }
            habitsReceiverRegistered = false;
        }
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
            int[] timeParts = TimeUtils.parseTimeParts(existing.getTime(), 12, 0);
            hour[0] = timeParts[0];
            minute[0] = timeParts[1];
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
        if (habit.isCompletedToday()) {
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
                try {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.optString("name", "").trim();
                    if (name.isEmpty()) {
                        continue;
                    }

                    int[] timeParts = TimeUtils.parseTimeParts(obj.optString("time", "12:00"), 12, 0);
                    String time = TimeUtils.format(timeParts[0], timeParts[1]);

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
                            name,
                            time,
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
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping invalid habit at index " + i, e);
                }
            }
            loadBlocks();
            loadWhitelistedApps();
            loadTimerBlocks();
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
                if (h.getDays() != null) {
                    for (Map.Entry<String, Boolean> e : h.getDays().entrySet()) {
                        daysObj.put(e.getKey(), e.getValue());
                    }
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
        scheduleAllBlocks();
        scheduleAllTimerBlocks();
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
        if (habitsReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(LockService.ACTION_HABITS_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(habitsUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(habitsUpdatedReceiver, filter);
        }
        habitsReceiverRegistered = true;
    }

    // --------- Blocks management ---------
    private void loadBlocks() {
        try {
            String json = prefs.getString("blocks", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(json);
            blockList.clear();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    java.util.Map<String, Boolean> days = new java.util.HashMap<>();
                    org.json.JSONObject daysObj = obj.optJSONObject("days");
                    if (daysObj != null) {
                        days.put("mon", daysObj.optBoolean("mon", false));
                        days.put("tue", daysObj.optBoolean("tue", false));
                        days.put("wed", daysObj.optBoolean("wed", false));
                        days.put("thu", daysObj.optBoolean("thu", false));
                        days.put("fri", daysObj.optBoolean("fri", false));
                        days.put("sat", daysObj.optBoolean("sat", false));
                        days.put("sun", daysObj.optBoolean("sun", false));
                    }
                    int[] startParts = TimeUtils.parseTimeParts(obj.optString("startTime", "00:00"), 0, 0);
                    int[] endParts = TimeUtils.parseTimeParts(obj.optString("endTime", "00:00"), 0, 0);
                    BlockPeriod bp = new BlockPeriod(
                            TimeUtils.format(startParts[0], startParts[1]),
                            TimeUtils.format(endParts[0], endParts[1]),
                            days);
                    bp.setName(obj.optString("name", ""));
                    bp.setEnabled(obj.optBoolean("enabled", true));
                    bp.setTimerMode(obj.optBoolean("timerMode", false));
                    blockList.add(bp);
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping invalid block at index " + i, e);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveBlocks() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (BlockPeriod b : blockList) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("name", b.getName());
                obj.put("startTime", b.getStartTime());
                obj.put("endTime", b.getEndTime());
                obj.put("enabled", b.isEnabled());
                obj.put("timerMode", b.isTimerMode());
                org.json.JSONObject daysObj = new org.json.JSONObject();
                if (b.getDays() != null) {
                    for (java.util.Map.Entry<String, Boolean> e : b.getDays().entrySet()) {
                        daysObj.put(e.getKey(), e.getValue());
                    }
                }
                obj.put("days", daysObj);
                arr.put(obj);
            }
            prefs.edit().putString("blocks", arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void scheduleAllBlocks() {
        for (BlockPeriod b : blockList) {
            BlockScheduler.schedule(this, b);
        }
    }

    private void showBlocksDialog() {
        loadBlocks();
        String[] items = new String[blockList.size() + 1];
        for (int i = 0; i < blockList.size(); i++) {
            BlockPeriod b = blockList.get(i);
            String name = b.getName().isEmpty() ? "" : b.getName() + " | ";
            String timer = b.isTimerMode() ? " ⏱️" : "";
            items[i] = name + b.getStartTime() + " - " + b.getEndTime() + timer;
        }
        items[blockList.size()] = "Добавить блокировку";

        new AlertDialog.Builder(this)
                .setTitle("Блокировки")
                .setItems(items, (dialog, which) -> {
                    if (which == blockList.size()) {
                        showAddBlockDialog(-1);
                    } else {
                        // options for existing block
                        int pos = which;
                        new AlertDialog.Builder(this)
                                .setItems(new String[]{"Редактировать","Удалить","Отмена"}, (d2, idx) -> {
                                    if (idx == 0) showAddBlockDialog(pos);
                                    else if (idx == 1) {
                                        BlockPeriod removed = blockList.remove(pos);
                                        BlockScheduler.cancel(this, removed);
                                        saveBlocks();
                                        Toast.makeText(this, "Блокировка удалена", Toast.LENGTH_SHORT).show();
                                    }
                                }).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showAddBlockDialog(int position) {
        boolean editing = position >= 0;
        BlockPeriod existing = editing ? blockList.get(position) : null;

        View view = getLayoutInflater().inflate(R.layout.dialog_add_block, null);
        EditText etBlockName = view.findViewById(R.id.etBlockName);
        Button btnStart = view.findViewById(R.id.btnSelectStartTime);
        Button btnEnd = view.findViewById(R.id.btnSelectEndTime);
        CheckBox chkMon = view.findViewById(R.id.chkMon);
        CheckBox chkTue = view.findViewById(R.id.chkTue);
        CheckBox chkWed = view.findViewById(R.id.chkWed);
        CheckBox chkThu = view.findViewById(R.id.chkThu);
        CheckBox chkFri = view.findViewById(R.id.chkFri);
        CheckBox chkSat = view.findViewById(R.id.chkSat);
        CheckBox chkSun = view.findViewById(R.id.chkSun);
        CheckBox chkTimerMode = view.findViewById(R.id.chkTimerMode);

        int[] startHour = {0};
        int[] startMinute = {0};
        int[] endHour = {10};
        int[] endMinute = {0};

        if (existing != null) {
            etBlockName.setText(existing.getName());
            int[] startParts = TimeUtils.parseTimeParts(existing.getStartTime(), 0, 0);
            startHour[0] = startParts[0];
            startMinute[0] = startParts[1];
            int[] endParts = TimeUtils.parseTimeParts(existing.getEndTime(), 10, 0);
            endHour[0] = endParts[0];
            endMinute[0] = endParts[1];
            setDayChecks(existing.getDays(), chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun);
            chkTimerMode.setChecked(existing.isTimerMode());
        }

        btnStart.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]));
        btnEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]));

        btnStart.setOnClickListener(v -> new TimePickerDialog(this,
                (view1, hourOfDay, minuteOfHour) -> {
                    startHour[0] = hourOfDay; startMinute[0] = minuteOfHour;
                    btnStart.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]));
                }, startHour[0], startMinute[0], true).show());

        btnEnd.setOnClickListener(v -> new TimePickerDialog(this,
                (view12, hourOfDay, minuteOfHour) -> {
                    endHour[0] = hourOfDay; endMinute[0] = minuteOfHour;
                    btnEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]));
                }, endHour[0], endMinute[0], true).show());

        new AlertDialog.Builder(this)
                .setTitle(editing ? "Изменить блок" : "Новая блокировка")
                .setView(view)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = etBlockName.getText().toString().trim();
                    String start = String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]);
                    String end = String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]);
                    java.util.Map<String, Boolean> days = collectDays(chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun);
                    BlockPeriod bp = new BlockPeriod(start, end, days);
                    bp.setName(name);
                    bp.setTimerMode(chkTimerMode.isChecked());
                    if (editing) {
                        BlockPeriod old = blockList.get(position);
                        BlockScheduler.cancel(this, old);
                        blockList.set(position, bp);
                    } else {
                        blockList.add(bp);
                    }
                    saveBlocks();
                    BlockScheduler.schedule(this, bp);
                    Toast.makeText(this, "Блокировка сохранена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // --------- Blocked Apps Management ---------
    private void loadBlockedApps() {
        try {
            String json = prefs.getString("blocked_apps", "[]");
            JSONArray arr = new JSONArray(json);
            blockedAppList.clear();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject obj = arr.getJSONObject(i);
                    String packageName = obj.optString("packageName", "").trim();
                    if (packageName.isEmpty()) {
                        continue;
                    }
                    BlockedApp app = new BlockedApp(
                            packageName,
                            obj.optString("appName", packageName),
                            obj.optString("blockType", "permanent")
                    );
                    int[] startParts = TimeUtils.parseTimeParts(obj.optString("startTime", "00:00"), 0, 0);
                    int[] endParts = TimeUtils.parseTimeParts(obj.optString("endTime", "23:59"), 23, 59);
                    app.setEnabled(obj.optBoolean("enabled", true));
                    app.setStartTime(TimeUtils.format(startParts[0], startParts[1]));
                    app.setEndTime(TimeUtils.format(endParts[0], endParts[1]));
                    blockedAppList.add(app);
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping invalid blocked app at index " + i, e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveBlockedApps() {
        try {
            JSONArray arr = new JSONArray();
            for (BlockedApp app : blockedAppList) {
                JSONObject obj = new JSONObject();
                obj.put("packageName", app.getPackageName());
                obj.put("appName", app.getAppName());
                obj.put("blockType", app.getBlockType());
                obj.put("enabled", app.isEnabled());
                obj.put("startTime", app.getStartTime());
                obj.put("endTime", app.getEndTime());
                arr.put(obj);
            }
            prefs.edit().putString("blocked_apps", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showBlockedAppsDialog() {
        loadBlockedApps();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Заблокированные приложения");
        
        View view = getLayoutInflater().inflate(R.layout.dialog_blocked_apps, null);
        RecyclerView recyclerView = view.findViewById(R.id.blockedAppsRecycler);
        Button btnAdd = view.findViewById(R.id.btnAddBlockedApp);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        final BlockedAppAdapter[] adapterHolder = {null};
        adapterHolder[0] = new BlockedAppAdapter(
                blockedAppList,
                this,
                app -> {
                    blockedAppList.remove(app);
                    saveBlockedApps();
                    adapterHolder[0].notifyDataSetChanged();
                    Toast.makeText(this, "Приложение удалено из списка блокировок", Toast.LENGTH_SHORT).show();
                },
                (app, enabled) -> saveBlockedApps()
        );
        recyclerView.setAdapter(adapterHolder[0]);
        
        btnAdd.setOnClickListener(v -> showSelectAppDialog());
        
        Button btnAddAll = view.findViewById(R.id.btnAddAllApps);
        btnAddAll.setOnClickListener(v -> addAllAppsToBlocked());
        
        Button btnDeleteAll = view.findViewById(R.id.btnDeleteAllApps);
        if (btnDeleteAll != null) {
            btnDeleteAll.setOnClickListener(v -> deleteAllBlockedApps());
        }
        
        builder.setView(view);
        if (!isAccessibilityServiceEnabled()) {
            builder.setPositiveButton("Включить сервис", (dialog, which) -> requestAccessibilityPermission());
        }
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private void showSelectAppDialog() {
        List<BlockedApp> availableApps = getInstalledApps();
        if (availableApps.isEmpty()) {
            Toast.makeText(this, "Нет приложений для добавления", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] appNames = new String[availableApps.size()];
        
        for (int i = 0; i < availableApps.size(); i++) {
            appNames[i] = availableApps.get(i).getAppName();
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Выбрать приложение для блокировки")
                .setItems(appNames, (dialog, which) -> {
                    BlockedApp selected = availableApps.get(which);
                    showBlockTypeDialog(selected);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void addAllAppsToBlocked() {
        List<BlockedApp> allApps = getInstalledApps();
        
        if (allApps.isEmpty()) {
            Toast.makeText(this, "Нет приложений для добавления", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Добавить все приложения?")
                .setMessage("Это добавит " + allApps.size() + " приложений в постоянную блокировку.\n\nЭто позволит открывать только приложения из белого списка.\n\nСистемные приложения автоматически исключены.")
                .setPositiveButton("Добавить всё", (dialog, which) -> {
                    for (BlockedApp app : allApps) {
                        app.setBlockType("permanent");
                        blockedAppList.add(app);
                    }
                    saveBlockedApps();
                    Toast.makeText(MainActivity.this, "Добавлено " + allApps.size() + " приложений", Toast.LENGTH_SHORT).show();
                    showBlockedAppsDialog(); // Перезагрузим диалог
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteAllBlockedApps() {
        if (blockedAppList.isEmpty()) {
            Toast.makeText(this, "Нет заблокированных приложений", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Удалить все заблокированные приложения?")
                .setMessage("Это удалит " + blockedAppList.size() + " заблокированных приложений из черного списка.")
                .setPositiveButton("Удалить всё", (dialog, which) -> {
                    blockedAppList.clear();
                    saveBlockedApps();
                    Toast.makeText(MainActivity.this, "Все приложения удалены из черного списка", Toast.LENGTH_SHORT).show();
                    showBlockedAppsDialog(); // Перезагрузим диалог
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showBlockTypeDialog(BlockedApp app) {
        String[] blockTypes = {"Постоянно", "По времени"};
        
        new AlertDialog.Builder(this)
                .setTitle("Тип блокировки для " + app.getAppName())
                .setItems(blockTypes, (dialog, which) -> {
                    if (which == 0) {
                        app.setBlockType("permanent");
                        blockedAppList.add(app);
                        saveBlockedApps();
                        Toast.makeText(this, app.getAppName() + " добавлено в постоянную блокировку", Toast.LENGTH_SHORT).show();
                    } else {
                        showTimeRangeDialog(app);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showTimeRangeDialog(BlockedApp app) {
        View view = getLayoutInflater().inflate(R.layout.dialog_block_time, null);
        Button btnStart = view.findViewById(R.id.btnBlockStartTime);
        Button btnEnd = view.findViewById(R.id.btnBlockEndTime);
        
        int[] startHour = {9};
        int[] startMinute = {0};
        int[] endHour = {17};
        int[] endMinute = {0};
        
        btnStart.setText("09:00");
        btnEnd.setText("17:00");
        
        btnStart.setOnClickListener(v -> new TimePickerDialog(this,
                (view1, hourOfDay, minuteOfHour) -> {
                    startHour[0] = hourOfDay;
                    startMinute[0] = minuteOfHour;
                    btnStart.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]));
                }, startHour[0], startMinute[0], true).show());
        
        btnEnd.setOnClickListener(v -> new TimePickerDialog(this,
                (view1, hourOfDay, minuteOfHour) -> {
                    endHour[0] = hourOfDay;
                    endMinute[0] = minuteOfHour;
                    btnEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]));
                }, endHour[0], endMinute[0], true).show());
        
        new AlertDialog.Builder(this)
                .setTitle("Время блокировки")
                .setView(view)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String start = String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]);
                    String end = String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]);
                    app.setBlockType("time_based");
                    app.setStartTime(start);
                    app.setEndTime(end);
                    blockedAppList.add(app);
                    saveBlockedApps();
                    Toast.makeText(this, app.getAppName() + " добавлено с временной блокировкой", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private List<BlockedApp> getInstalledApps() {
        List<BlockedApp> apps = new ArrayList<>();
        List<String> blockedPackages = new ArrayList<>();
        
        for (BlockedApp blocked : blockedAppList) {
            blockedPackages.add(blocked.getPackageName());
        }
        
        // Только самые критичные системные приложения для исключения
        String[] systemPackagesToExclude = {
                "com.android.systemui",
                "com.android.launcher",
                "com.android.launcher3",
                "com.samsung.android.launcher",
                "com.sec.android.app.launcher",
                "com.miui.home",
                "com.android.settings",
                "com.android.permissioncontroller",
                "com.android.packageinstaller"
        };
        
        android.content.pm.PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(0);
        
        Log.d("AppBlocker", "Total installed apps: " + packages.size());
        
        for (android.content.pm.ApplicationInfo appInfo : packages) {
            String packageName = appInfo.packageName;
            String appName = "";
            try {
                appName = pm.getApplicationLabel(appInfo).toString();
            } catch (Exception e) {
                appName = packageName;
            }
            
            // Пропускаем если уже в черном списке
            if (blockedPackages.contains(packageName)) {
                Log.d("AppBlocker", "SKIP (in blocked list): " + packageName);
                continue;
            }
            
            // Пропускаем само приложение Strict Habits
            if (packageName.equals(getPackageName())) {
                Log.d("AppBlocker", "SKIP (self): " + packageName);
                continue;
            }
            
            // Пропускаем только самые критичные системные приложения
            boolean isSystemByName = false;
            for (String systemPackage : systemPackagesToExclude) {
                if (packageName.equals(systemPackage)) {
                    isSystemByName = true;
                    Log.d("AppBlocker", "SKIP (critical system): " + packageName);
                    break;
                }
            }
            if (isSystemByName) {
                continue;
            }
            
            Log.d("AppBlocker", "ADD to list: " + packageName + " (" + appName + ")");
            apps.add(new BlockedApp(packageName, appName, "permanent"));
        }
        
        Log.d("AppBlocker", "Total apps to show in blocked list: " + apps.size());
        
        // Sort by name
        apps.sort((a, b) -> a.getAppName().compareTo(b.getAppName()));
        return apps;
    }

    private void requestAccessibilityPermission() {
        if (isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Сервис блокировки приложений уже включен", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Включите 'Strict Habits' в Accessibility Services", Toast.LENGTH_LONG).show();
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null || enabledServices.isEmpty()) {
            return false;
        }

        ComponentName expected = new ComponentName(this, AppBlockAccessibilityService.class);
        String expectedName = expected.flattenToString();
        String[] services = enabledServices.split(":");
        for (String service : services) {
            if (expectedName.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }

    // --------- Whitelisted Apps Management ---------
    private void loadWhitelistedApps() {
        try {
            String json = prefs.getString("whitelisted_apps", "[]");
            JSONArray arr = new JSONArray(json);
            whitelistedAppList.clear();

            // Если белый список пустой, добавляем дефолтные приложения
            if (arr.length() == 0) {
                addDefaultWhitelistedApps();
                saveWhitelistedApps();
                json = prefs.getString("whitelisted_apps", "[]");
                arr = new JSONArray(json);
            }

            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject obj = arr.getJSONObject(i);
                    String packageName = obj.optString("packageName", "").trim();
                    if (packageName.isEmpty()) {
                        continue;
                    }
                    WhitelistedApp app = new WhitelistedApp(
                            packageName,
                            obj.optString("appName", packageName)
                    );
                    app.setEnabled(obj.optBoolean("enabled", true));
                    whitelistedAppList.add(app);
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping invalid whitelisted app at index " + i, e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addDefaultWhitelistedApps() {
        // Образовательные и полезные приложения
        String[] defaultApps = {
                "com.openai.chatgpt", "ChatGPT",
                "com.deepseek.app", "DeepSeek",
                "ai.deepseek.app", "DeepSeek",
                "com.anthropic.claude", "Claude",
                "com.google.android.apps.docs", "Google Docs",
                "com.google.android.apps.docs.editors.docs", "Google Docs",
                "com.google.android.apps.docs.editors.sheets", "Google Sheets",
                "com.google.android.apps.docs.editors.slides", "Google Slides",
                "com.microsoft.office.word", "Microsoft Word",
                "com.microsoft.office.excel", "Microsoft Excel",
                "com.microsoft.office.powerpoint", "Microsoft PowerPoint",
                "com.evernote", "Evernote",
                "com.google.android.apps.books", "Google Play Books",
                "com.amazon.kindle", "Kindle",
                "org.librera.reader", "Librera",
                "com.duolingo", "Duolingo",
                "com.khanacademy.android", "Khan Academy",
                "com.coursera.android", "Coursera",
                "com.udemy.android", "Udemy",
                "com.google.android.apps.maps", "Google Maps",
                "com.google.android.apps.translate", "Google Translate",
                "com.google.android.youtube", "YouTube",
                "com.google.android.apps.youtube.music", "YouTube Music",
                "com.spotify.music", "Spotify",
                "com.google.android.calculator", "Calculator",
                "com.google.android.calendar", "Calendar",
                "com.google.android.apps.tasks", "Google Tasks",
                "com.todoist", "Todoist",
                "com.microsoft.todos", "Microsoft To Do",
                "org.mozilla.firefox", "Firefox",
                "com.android.chrome", "Chrome",
                "com.microsoft.emmx", "Microsoft Edge",
                "com.google.android.gm", "Gmail",
                "com.microsoft.office.outlook", "Outlook",
                "com.whatsapp", "WhatsApp",
                "org.telegram.messenger", "Telegram",
                "com.discord", "Discord",
                "us.zoom.videomeetings", "Zoom",
                "com.microsoft.teams", "Microsoft Teams",
                "com.google.android.apps.messaging", "Messages",
                "com.android.camera", "Camera",
                "com.android.camera2", "Camera",
                "com.google.android.GoogleCamera", "Google Camera",
                "com.sec.android.app.camera", "Samsung Camera",
                "com.android.contacts", "Contacts",
                "com.google.android.contacts", "Contacts",
                "com.android.vending", "Google Play Store",
                "com.android.settings", "Settings",
                "com.stricthabits.app", "Strict Habits"
        };

        for (int i = 0; i < defaultApps.length; i += 2) {
            String packageName = defaultApps[i];
            String appName = defaultApps[i + 1];

            // Проверяем, установлено ли приложение
            if (isAppInstalled(packageName)) {
                WhitelistedApp app = new WhitelistedApp(packageName, appName);
                app.setEnabled(true);
                whitelistedAppList.add(app);
            }
        }
    }

    private boolean isAppInstalled(String packageName) {
        try {
            getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void saveWhitelistedApps() {
        try {
            JSONArray arr = new JSONArray();
            for (WhitelistedApp app : whitelistedAppList) {
                JSONObject obj = new JSONObject();
                obj.put("packageName", app.getPackageName());
                obj.put("appName", app.getAppName());
                obj.put("enabled", app.isEnabled());
                arr.put(obj);
            }
            prefs.edit().putString("whitelisted_apps", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showWhitelistedAppsDialog() {
        loadWhitelistedApps();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Белый список приложений");
        
        View view = getLayoutInflater().inflate(R.layout.dialog_whitelisted_apps, null);
        RecyclerView recyclerView = view.findViewById(R.id.whitelistedAppsRecycler);
        Button btnAdd = view.findViewById(R.id.btnAddWhitelistedApp);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        final WhitelistedAppAdapter[] adapterHolder = {null};
        adapterHolder[0] = new WhitelistedAppAdapter(
                whitelistedAppList,
                this,
                app -> {
                    whitelistedAppList.remove(app);
                    saveWhitelistedApps();
                    adapterHolder[0].notifyDataSetChanged();
                    Toast.makeText(this, "Приложение удалено из белого списка", Toast.LENGTH_SHORT).show();
                },
                (app, enabled) -> saveWhitelistedApps()
        );
        recyclerView.setAdapter(adapterHolder[0]);
        
        btnAdd.setOnClickListener(v -> showSelectAppForWhitelistDialog());
        
        builder.setView(view);
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private void showSelectAppForWhitelistDialog() {
        List<WhitelistedApp> availableApps = getInstalledAppsForWhitelist();
        if (availableApps.isEmpty()) {
            Toast.makeText(this, "Нет приложений для добавления", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] appNames = new String[availableApps.size()];
        
        for (int i = 0; i < availableApps.size(); i++) {
            appNames[i] = availableApps.get(i).getAppName();
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Выбрать приложение для белого списка")
                .setItems(appNames, (dialog, which) -> {
                    WhitelistedApp selected = availableApps.get(which);
                    whitelistedAppList.add(selected);
                    saveWhitelistedApps();
                    Toast.makeText(this, selected.getAppName() + " добавлено в белый список", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private List<WhitelistedApp> getInstalledAppsForWhitelist() {
        List<WhitelistedApp> apps = new ArrayList<>();
        List<String> whitelistedPackages = new ArrayList<>();
        
        for (WhitelistedApp whitelisted : whitelistedAppList) {
            whitelistedPackages.add(whitelisted.getPackageName());
        }
        
        // Только самые критичные системные приложения для исключения
        String[] systemPackagesToExclude = {
                "com.android.systemui",
                "com.android.launcher",
                "com.android.launcher3",
                "com.samsung.android.launcher",
                "com.sec.android.app.launcher",
                "com.miui.home",
                "com.android.settings",
                "com.android.permissioncontroller",
                "com.android.packageinstaller"
        };
        
        android.content.pm.PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(0);
        
        for (android.content.pm.ApplicationInfo appInfo : packages) {
            String packageName = appInfo.packageName;
            
            // Пропускаем если уже в белом списке
            if (whitelistedPackages.contains(packageName)) {
                continue;
            }
            
            // Пропускаем само приложение Strict Habits
            if (packageName.equals(getPackageName())) {
                continue;
            }
            
            // Пропускаем только самые критичные системные приложения
            boolean isSystemByName = false;
            for (String systemPackage : systemPackagesToExclude) {
                if (packageName.equals(systemPackage)) {
                    isSystemByName = true;
                    break;
                }
            }
            if (isSystemByName) {
                continue;
            }
            
            String appName = pm.getApplicationLabel(appInfo).toString();
            apps.add(new WhitelistedApp(packageName, appName));
        }
        
        // Sort by name
        apps.sort((a, b) -> a.getAppName().compareTo(b.getAppName()));
        return apps;
    }

    private void showTimerLockDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_timer_lock, null);
        EditText etName = view.findViewById(R.id.etTimerName);
        EditText etMinutes = view.findViewById(R.id.etTimerMinutes);

        new AlertDialog.Builder(this)
                .setTitle("Таймер-блокировка")
                .setView(view)
                .setPositiveButton("Запустить", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String minutesStr = etMinutes.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (minutesStr.isEmpty()) {
                        Toast.makeText(this, "Введите длительность", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int minutes;
                    try {
                        minutes = Integer.parseInt(minutesStr);
                        if (minutes <= 0 || minutes > 1440) {
                            Toast.makeText(this, "Длительность должна быть от 1 до 1440 минут", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Неверный формат числа", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "Для таймер-блокировки нужно разрешение поверх окон", Toast.LENGTH_LONG).show();
                        requestOverlayPermission();
                        return;
                    }

                    startTimerLock(name, minutes);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // --------- Timer Blocks management ---------
    private void loadTimerBlocks() {
        try {
            String json = prefs.getString("timer_blocks", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(json);
            timerBlockList.clear();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    java.util.Map<String, Boolean> days = new java.util.HashMap<>();
                    org.json.JSONObject daysObj = obj.optJSONObject("days");
                    if (daysObj != null) {
                        days.put("mon", daysObj.optBoolean("mon", false));
                        days.put("tue", daysObj.optBoolean("tue", false));
                        days.put("wed", daysObj.optBoolean("wed", false));
                        days.put("thu", daysObj.optBoolean("thu", false));
                        days.put("fri", daysObj.optBoolean("fri", false));
                        days.put("sat", daysObj.optBoolean("sat", false));
                        days.put("sun", daysObj.optBoolean("sun", false));
                    }
                    int[] startParts = TimeUtils.parseTimeParts(obj.optString("startTime", "00:00"), 0, 0);
                    int[] endParts = TimeUtils.parseTimeParts(obj.optString("endTime", "00:00"), 0, 0);
                    TimerBlock tb = new TimerBlock(
                            obj.optString("name", "Таймер"),
                            TimeUtils.format(startParts[0], startParts[1]),
                            TimeUtils.format(endParts[0], endParts[1]),
                            days);
                    tb.setEnabled(obj.optBoolean("enabled", true));
                    timerBlockList.add(tb);
                } catch (Exception e) {
                    Log.w("MainActivity", "Skipping invalid timer block at index " + i, e);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveTimerBlocks() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (TimerBlock tb : timerBlockList) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("name", tb.getName());
                obj.put("startTime", tb.getStartTime());
                obj.put("endTime", tb.getEndTime());
                obj.put("enabled", tb.isEnabled());
                org.json.JSONObject daysObj = new org.json.JSONObject();
                if (tb.getDays() != null) {
                    for (java.util.Map.Entry<String, Boolean> e : tb.getDays().entrySet()) {
                        daysObj.put(e.getKey(), e.getValue());
                    }
                }
                obj.put("days", daysObj);
                arr.put(obj);
            }
            prefs.edit().putString("timer_blocks", arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void scheduleAllTimerBlocks() {
        for (TimerBlock tb : timerBlockList) {
            TimerBlockScheduler.schedule(this, tb);
        }
    }

    private void showTimerBlocksDialog() {
        loadTimerBlocks();
        String[] items = new String[timerBlockList.size() + 1];
        for (int i = 0; i < timerBlockList.size(); i++) {
            TimerBlock tb = timerBlockList.get(i);
            items[i] = "⏱️ " + tb.getName() + " | " + tb.getStartTime() + " - " + tb.getEndTime();
        }
        items[timerBlockList.size()] = "➕ Добавить таймер-блокировку";

        new AlertDialog.Builder(this)
                .setTitle("Таймер-блокировки (расписание)")
                .setItems(items, (dialog, which) -> {
                    if (which == timerBlockList.size()) {
                        showAddTimerBlockDialog(-1);
                    } else {
                        // options for existing timer block
                        int pos = which;
                        new AlertDialog.Builder(this)
                                .setItems(new String[]{"Редактировать","Удалить","Отмена"}, (d2, idx) -> {
                                    if (idx == 0) showAddTimerBlockDialog(pos);
                                    else if (idx == 1) {
                                        TimerBlock removed = timerBlockList.remove(pos);
                                        TimerBlockScheduler.cancel(this, removed);
                                        saveTimerBlocks();
                                        Toast.makeText(this, "Таймер-блокировка удалена", Toast.LENGTH_SHORT).show();
                                    }
                                }).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showAddTimerBlockDialog(int position) {
        boolean editing = position >= 0;
        TimerBlock existing = editing ? timerBlockList.get(position) : null;

        View view = getLayoutInflater().inflate(R.layout.dialog_add_timer_block, null);
        EditText etTimerBlockName = view.findViewById(R.id.etTimerBlockName);
        Button btnStart = view.findViewById(R.id.btnSelectStartTime);
        Button btnEnd = view.findViewById(R.id.btnSelectEndTime);
        CheckBox chkMon = view.findViewById(R.id.chkMon);
        CheckBox chkTue = view.findViewById(R.id.chkTue);
        CheckBox chkWed = view.findViewById(R.id.chkWed);
        CheckBox chkThu = view.findViewById(R.id.chkThu);
        CheckBox chkFri = view.findViewById(R.id.chkFri);
        CheckBox chkSat = view.findViewById(R.id.chkSat);
        CheckBox chkSun = view.findViewById(R.id.chkSun);

        int[] startHour = {9};
        int[] startMinute = {0};
        int[] endHour = {17};
        int[] endMinute = {0};

        if (existing != null) {
            etTimerBlockName.setText(existing.getName());
            int[] startParts = TimeUtils.parseTimeParts(existing.getStartTime(), 9, 0);
            startHour[0] = startParts[0];
            startMinute[0] = startParts[1];
            int[] endParts = TimeUtils.parseTimeParts(existing.getEndTime(), 17, 0);
            endHour[0] = endParts[0];
            endMinute[0] = endParts[1];
            setDayChecks(existing.getDays(), chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun);
        }

        btnStart.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]));
        btnEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]));

        btnStart.setOnClickListener(v -> new TimePickerDialog(this,
                (view1, hourOfDay, minuteOfHour) -> {
                    startHour[0] = hourOfDay; startMinute[0] = minuteOfHour;
                    btnStart.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]));
                }, startHour[0], startMinute[0], true).show());

        btnEnd.setOnClickListener(v -> new TimePickerDialog(this,
                (view12, hourOfDay, minuteOfHour) -> {
                    endHour[0] = hourOfDay; endMinute[0] = minuteOfHour;
                    btnEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]));
                }, endHour[0], endMinute[0], true).show());

        new AlertDialog.Builder(this)
                .setTitle(editing ? "Изменить таймер-блокировку" : "Новая таймер-блокировка")
                .setView(view)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = etTimerBlockName.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "Таймер-блокировка";
                    }
                    String start = String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]);
                    String end = String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]);
                    java.util.Map<String, Boolean> days = collectDays(chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun);
                    TimerBlock tb = new TimerBlock(name, start, end, days);
                    if (editing) {
                        TimerBlock old = timerBlockList.get(position);
                        TimerBlockScheduler.cancel(this, old);
                        timerBlockList.set(position, tb);
                    } else {
                        timerBlockList.add(tb);
                    }
                    saveTimerBlocks();
                    TimerBlockScheduler.schedule(this, tb);
                    Toast.makeText(this, "Таймер-блокировка сохранена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void startTimerLock(String name, int minutes) {
        Intent intent = new Intent(this, TimerLockService.class);
        intent.putExtra("lock_name", name);
        intent.putExtra("duration_minutes", minutes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(this, "Таймер запущен: " + minutes + " мин", Toast.LENGTH_SHORT).show();
    }

    private void syncWithDesktop() {
        try {
            if (syncManager == null) {
                syncManager = new SyncManager(this);
            }

            boolean imported = syncManager.importFromSyncFile();
            if (imported) {
                loadHabits();
                scheduleAllHabits();
                refreshHabits();
            }

            boolean exported = syncManager.exportToSyncFile();

            if (exported && imported) {
                Toast.makeText(this, "Синхронизация завершена!\nФайл: " + syncManager.getSyncFilePath(), Toast.LENGTH_LONG).show();
            } else if (exported) {
                Toast.makeText(this, "Данные экспортированы в:\n" + syncManager.getSyncFilePath(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Ошибка синхронизации", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Sync error", e);
            Toast.makeText(this, "Ошибка синхронизации: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
