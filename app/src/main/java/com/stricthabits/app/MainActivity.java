package com.stricthabits.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                habit -> testHabit(habit),
                (position, completed) -> {
                    habitList.get(position).setCompletedToday(completed);
                    saveHabits();
                    Toast.makeText(this, completed ? "Выполнено!" : "Отмечено как невыполненное", Toast.LENGTH_SHORT).show();
                });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddHabit).setOnClickListener(v -> showAddDialog());
        View btnOverlay = findViewById(R.id.btnRequestOverlay);
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            btnOverlay.setVisibility(View.VISIBLE);
            requestOverlayPermission();
        } else {
            btnOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        View btnOverlay = findViewById(R.id.btnRequestOverlay);
        if (btnOverlay != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                btnOverlay.setVisibility(View.GONE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                btnOverlay.setVisibility(View.VISIBLE);
            }
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
        CheckBox chkSound = view.findViewById(R.id.chkSound);

        // Дни недели
        CheckBox chkMon = view.findViewById(R.id.chkMon);
        CheckBox chkTue = view.findViewById(R.id.chkTue);
        CheckBox chkWed = view.findViewById(R.id.chkWed);
        CheckBox chkThu = view.findViewById(R.id.chkThu);
        CheckBox chkFri = view.findViewById(R.id.chkFri);
        CheckBox chkSat = view.findViewById(R.id.chkSat);
        CheckBox chkSun = view.findViewById(R.id.chkSun);

        int[] hour = {12};
        int[] minute = {0};
        btnTime.setText("12:00");
        btnTime.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(this,
                    (view1, hourOfDay, minuteOfHour) -> {
                        hour[0] = hourOfDay;
                        minute[0] = minuteOfHour;
                        btnTime.setText(String.format("%02d:%02d", hour[0], minute[0]));
                    }, hour[0], minute[0], true);
            timePicker.show();
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
                    Map<String, Boolean> days = new HashMap<>();
                    days.put("mon", chkMon.isChecked());
                    days.put("tue", chkTue.isChecked());
                    days.put("wed", chkWed.isChecked());
                    days.put("thu", chkThu.isChecked());
                    days.put("fri", chkFri.isChecked());
                    days.put("sat", chkSat.isChecked());
                    days.put("sun", chkSun.isChecked());

                    Habit habit = new Habit(name, time, chkSound.isChecked(), days);
                    habitList.add(habit);
                    saveHabits();
                    adapter.notifyItemInserted(habitList.size() - 1);
                    HabitScheduler.schedule(this, habit);
                })
                .show();
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
        intent.putExtra("sound_enabled", habit.isSoundEnabled());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void loadHabits() {
        try {
            String json = prefs.getString("list", "[]");
            JSONArray arr = new JSONArray(json);
            habitList.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("name");
                String time = obj.getString("time");
                boolean sound = obj.getBoolean("soundEnabled");
                boolean completed = obj.optBoolean("completedToday", false);
                Map<String, Boolean> days = new HashMap<>();
                JSONObject daysObj = obj.getJSONObject("days");
                days.put("mon", daysObj.optBoolean("mon", false));
                days.put("tue", daysObj.optBoolean("tue", false));
                days.put("wed", daysObj.optBoolean("wed", false));
                days.put("thu", daysObj.optBoolean("thu", false));
                days.put("fri", daysObj.optBoolean("fri", false));
                days.put("sat", daysObj.optBoolean("sat", false));
                days.put("sun", daysObj.optBoolean("sun", false));
                Habit h = new Habit(name, time, sound, days);
                h.setCompletedToday(completed);
                habitList.add(h);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveHabits() {
        try {
            JSONArray arr = new JSONArray();
            for (Habit h : habitList) {
                JSONObject obj = new JSONObject();
                obj.put("name", h.getName());
                obj.put("time", h.getTime());
                obj.put("soundEnabled", h.isSoundEnabled());
                obj.put("completedToday", h.isCompletedToday());
                JSONObject daysObj = new JSONObject();
                for (Map.Entry<String, Boolean> e : h.getDays().entrySet()) {
                    daysObj.put(e.getKey(), e.getValue());
                }
                obj.put("days", daysObj);
                arr.put(obj);
            }
            prefs.edit().putString("list", arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }
}