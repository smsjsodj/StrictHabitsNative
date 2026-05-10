package com.stricthabits.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
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
        adapter = new HabitAdapter(habitList,
                position -> deleteHabit(position),
                habit -> testHabit(habit),
                (position, completed) -> {
                    habitList.get(position).setCompletedToday(completed);
                    saveHabits();
                });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddHabit).setOnClickListener(v -> showAddDialog());

        // Запрос разрешения на оверлей
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
        CheckBox chkMon = view.findViewById(R.id.chkMon);
        CheckBox chkTue = view.findViewById(R.id.chkTue);
        CheckBox chkWen = view.findViewById(R.id.chkWen);
        CheckBox chkThu = view.findViewById(R.id.chkThu);
        CheckBox chkFri = view.findViewById(R.id.chkFri);
        CheckBox chkSat = view.findViewById(R.id.chkSat);
        CheckBox chkSun = view.findViewById(R.id.chkSun);
        CheckBox chkSound = view.findViewById(R.id.chkSound);

        Map<String, Boolean> days = new HashMap<>();
        days.put("Mon", true);
        days.put("Tue", true);
        days.put("Wed", true);
        days.put("Thu", true);
        days.put("Fri", true);
        days.put("Sat", true);
        days.put("Sun", true);

        chkMon.setChecked(true);
        chkTue.setChecked(true);
        chkWen.setChecked(true);
        chkThu.setChecked(true);
        chkFri.setChecked(true);
        chkSat.setChecked(true);
        chkSun.setChecked(true);

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
                    days.put("Mon", chkMon.isChecked());
                    days.put("Tue", chkTue.isChecked());
                    days.put("Wed", chkWen.isChecked());
                    days.put("Thu", chkThu.isChecked());
                    days.put("Fri", chkFri.isChecked());
                    days.put("Sat", chkSat.isChecked());
                    days.put("Sun", chkSun.isChecked());
                    Habit habit = new Habit(name, time, chkSound.isChecked(), days);
                    habitList.add(habit);
                    saveHabits();
                    adapter.notifyItemInserted(habitList.size() - 1);
                    scheduleAlarm(habit);
                })
                .show();
    }

    private void scheduleAlarm(Habit habit) {
        // Проверяем, нужен ли сегодня будильник
        String today = getToday();
        if (habit.getDays().get(today)) {
            HabitScheduler.scheduleOnce(this, habit);
        }
    }

    private String getToday() {
        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.MONDAY: return "Mon";
            case Calendar.TUESDAY: return "Tue";
            case Calendar.WEDNESDAY: return "Wed";
            case Calendar.THURSDAY: return "Thu";
            case Calendar.FRIDAY: return "Fri";
            case Calendar.SATURDAY: return "Sat";
            case Calendar.SUNDAY: return "Sun";
            default: return "Mon";
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
                String name = obj.getString("name");
                String time = obj.getString("time");
                boolean sound = obj.getBoolean("sound");
                boolean completed = obj.getBoolean("completed");
                JSONObject daysObj = obj.getJSONObject("days");
                Map<String, Boolean> days = new HashMap<>();
                days.put("Mon", daysObj.getBoolean("Mon"));
                days.put("Tue", daysObj.getBoolean("Tue"));
                days.put("Wed", daysObj.getBoolean("Wed"));
                days.put("Thu", daysObj.getBoolean("Thu"));
                days.put("Fri", daysObj.getBoolean("Fri"));
                days.put("Sat", daysObj.getBoolean("Sat"));
                days.put("Sun", daysObj.getBoolean("Sun"));
                Habit h = new Habit(name, time, sound, days);
                h.setCompletedToday(completed);
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
                obj.put("sound", h.isSoundEnabled());
                obj.put("completed", h.isCompletedToday());
                JSONObject daysObj = new JSONObject();
                Map<String, Boolean> days = h.getDays();
                daysObj.put("Mon", days.get("Mon"));
                daysObj.put("Tue", days.get("Tue"));
                daysObj.put("Wed", days.get("Wed"));
                daysObj.put("Thu", days.get("Thu"));
                daysObj.put("Fri", days.get("Fri"));
                daysObj.put("Sat", days.get("Sat"));
                daysObj.put("Sun", days.get("Sun"));
                obj.put("days", daysObj);
                arr.put(obj);
            }
            prefs.edit().putString("list", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}