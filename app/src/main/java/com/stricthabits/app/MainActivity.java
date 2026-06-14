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
    private final List<BlockPeriod> blockList = new ArrayList<>();
    private final List<BlockedApp> blockedAppList = new ArrayList<>();
    private final List<WhitelistedApp> whitelistedAppList = new ArrayList<>();
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
        findViewById(R.id.btnManageBlocks).setOnClickListener(v -> showBlocksDialog());

        Button btnBlockedApps = findViewById(R.id.btnBlockedApps);
        if (btnBlockedApps != null) {
            btnBlockedApps.setOnClickListener(v -> showBlockedAppsDialog());
        }

        Button btnWhitelistedApps = findViewById(R.id.btnWhitelistedApps);
        if (btnWhitelistedApps != null) {
            btnWhitelistedApps.setOnClickListener(v -> showWhitelistedAppsDialog());
        }

        View btnOverlay = findViewById(R.id.btnRequestOverlay);
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());
        updateOverlayButton();
        requestExactAlarmPermission();
        requestNotificationPermission();
        requestUsageStatsPermission();
        startAppBlockService();
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
            loadBlocks();
            loadWhitelistedApps();
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
        scheduleAllBlocks();
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

    // --------- Blocks management ---------
    private void loadBlocks() {
        try {
            String json = prefs.getString("blocks", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(json);
            blockList.clear();
            for (int i = 0; i < arr.length(); i++) {
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
                BlockPeriod bp = new BlockPeriod(obj.optString("startTime", "00:00"), obj.optString("endTime", "00:00"), days);
                bp.setEnabled(obj.optBoolean("enabled", true));
                blockList.add(bp);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveBlocks() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (BlockPeriod b : blockList) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("startTime", b.getStartTime());
                obj.put("endTime", b.getEndTime());
                obj.put("enabled", b.isEnabled());
                org.json.JSONObject daysObj = new org.json.JSONObject();
                for (java.util.Map.Entry<String, Boolean> e : b.getDays().entrySet()) {
                    daysObj.put(e.getKey(), e.getValue());
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
            items[i] = b.getStartTime() + " - " + b.getEndTime();
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
        Button btnStart = view.findViewById(R.id.btnSelectStartTime);
        Button btnEnd = view.findViewById(R.id.btnSelectEndTime);
        CheckBox chkMon = view.findViewById(R.id.chkMon);
        CheckBox chkTue = view.findViewById(R.id.chkTue);
        CheckBox chkWed = view.findViewById(R.id.chkWed);
        CheckBox chkThu = view.findViewById(R.id.chkThu);
        CheckBox chkFri = view.findViewById(R.id.chkFri);
        CheckBox chkSat = view.findViewById(R.id.chkSat);
        CheckBox chkSun = view.findViewById(R.id.chkSun);

        int[] startHour = {0};
        int[] startMinute = {0};
        int[] endHour = {10};
        int[] endMinute = {0};

        if (existing != null) {
            String[] sp = existing.getStartTime().split(":");
            startHour[0] = Integer.parseInt(sp[0]);
            startMinute[0] = Integer.parseInt(sp[1]);
            String[] ep = existing.getEndTime().split(":");
            endHour[0] = Integer.parseInt(ep[0]);
            endMinute[0] = Integer.parseInt(ep[1]);
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
                .setTitle(editing ? "Изменить блок" : "Новая блокировка")
                .setView(view)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String start = String.format(Locale.getDefault(), "%02d:%02d", startHour[0], startMinute[0]);
                    String end = String.format(Locale.getDefault(), "%02d:%02d", endHour[0], endMinute[0]);
                    java.util.Map<String, Boolean> days = collectDays(chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun);
                    BlockPeriod bp = new BlockPeriod(start, end, days);
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
                JSONObject obj = arr.getJSONObject(i);
                BlockedApp app = new BlockedApp(
                        obj.getString("packageName"),
                        obj.getString("appName"),
                        obj.optString("blockType", "permanent")
                );
                app.setEnabled(obj.optBoolean("enabled", true));
                app.setStartTime(obj.optString("startTime", "00:00"));
                app.setEndTime(obj.optString("endTime", "23:59"));
                blockedAppList.add(app);
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
        BlockedAppAdapter adapter = new BlockedAppAdapter(
                blockedAppList,
                this,
                app -> {
                    blockedAppList.remove(app);
                    saveBlockedApps();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Приложение удалено из списка блокировок", Toast.LENGTH_SHORT).show();
                },
                (app, enabled) -> saveBlockedApps()
        );
        recyclerView.setAdapter(adapter);
        
        btnAdd.setOnClickListener(v -> showSelectAppDialog());
        
        builder.setView(view);
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private void showSelectAppDialog() {
        List<BlockedApp> availableApps = getInstalledApps();
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
        
        android.content.pm.PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(0);
        
        for (android.content.pm.ApplicationInfo appInfo : packages) {
            if (!blockedPackages.contains(appInfo.packageName)
                    && !appInfo.packageName.equals(getPackageName())) {
                String appName = pm.getApplicationLabel(appInfo).toString();
                apps.add(new BlockedApp(appInfo.packageName, appName, "permanent"));
            }
        }
        
        // Sort by name
        apps.sort((a, b) -> a.getAppName().compareTo(b.getAppName()));
        return apps;
    }

    private void requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        }
    }

    private void startAppBlockService() {
        Intent intent = new Intent(this, AppBlockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // --------- Whitelisted Apps Management ---------
    private void loadWhitelistedApps() {
        try {
            String json = prefs.getString("whitelisted_apps", "[]");
            JSONArray arr = new JSONArray(json);
            whitelistedAppList.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                WhitelistedApp app = new WhitelistedApp(
                        obj.getString("packageName"),
                        obj.getString("appName")
                );
                app.setEnabled(obj.optBoolean("enabled", true));
                whitelistedAppList.add(app);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        WhitelistedAppAdapter adapter = new WhitelistedAppAdapter(
                whitelistedAppList,
                this,
                app -> {
                    whitelistedAppList.remove(app);
                    saveWhitelistedApps();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Приложение удалено из белого списка", Toast.LENGTH_SHORT).show();
                },
                (app, enabled) -> saveWhitelistedApps()
        );
        recyclerView.setAdapter(adapter);
        
        btnAdd.setOnClickListener(v -> showSelectAppForWhitelistDialog());
        
        builder.setView(view);
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private void showSelectAppForWhitelistDialog() {
        List<WhitelistedApp> availableApps = getInstalledAppsForWhitelist();
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
        
        android.content.pm.PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(0);
        
        for (android.content.pm.ApplicationInfo appInfo : packages) {
            if (!whitelistedPackages.contains(appInfo.packageName)
                    && !appInfo.packageName.equals(getPackageName())) {
                String appName = pm.getApplicationLabel(appInfo).toString();
                apps.add(new WhitelistedApp(appInfo.packageName, appName));
            }
        }
        
        // Sort by name
        apps.sort((a, b) -> a.getAppName().compareTo(b.getAppName()));
        return apps;
    }
}
