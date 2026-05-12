package com.stricthabits.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class LockActivity extends AppCompatActivity {
    private BroadcastReceiver habitsUpdatedReceiver;
    private String habitName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lock_screen);

        Intent intent = getIntent();
        habitName = intent.getStringExtra("habit_name");
        String habitTime = intent.getStringExtra("habit_time");
        String unlockMode = intent.getStringExtra(LockService.EXTRA_UNLOCK_MODE);

        TextView tvName = findViewById(R.id.tvHabitName);
        TextView tvTime = findViewById(R.id.tvTime);
        EditText etConfirm = findViewById(R.id.etConfirm);
        Button btnUnlock = findViewById(R.id.btnUnlock);

        tvName.setText(habitName == null ? "" : habitName);
        tvTime.setText(habitTime == null ? "" : habitTime);

        if (LockService.UNLOCK_MODE_TELEGRAM.equals(unlockMode)) {
            tvTime.setText("Telegram: /unlock");
            etConfirm.setVisibility(View.GONE);
            btnUnlock.setVisibility(View.GONE);
        }

        btnUnlock.setOnClickListener(v -> {
            String text = etConfirm.getText().toString().trim();
            if (text.equalsIgnoreCase("\u044f \u043a\u043b\u044f\u043d\u0443\u0441\u044c \u0436\u043e\u043f\u043e\u0439")) {
                markHabitCompleted(habitName);
                finish();
            } else {
                Toast.makeText(LockActivity.this,
                        "\u041d\u0435\u0432\u0435\u0440\u043d\u0430\u044f \u0444\u0440\u0430\u0437\u0430",
                        Toast.LENGTH_SHORT).show();
            }
        });

        habitsUpdatedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
        registerReceiver(habitsUpdatedReceiver, new IntentFilter(LockService.ACTION_HABITS_UPDATED));
    }

    private void markHabitCompleted(String name) {
        if (name == null || name.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("habits", MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString("list", "[]"));
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (name.equals(obj.optString("name", ""))) {
                    String previousDate = obj.optString("lastCompletedDate", "");
                    if (!today.equals(previousDate)) {
                        obj.put("completedCount", obj.optInt("completedCount", 0) + 1);
                    }
                    obj.put("lastCompletedDate", today);
                    obj.put("skippedDate", "");
                    changed = true;
                    break;
                }
            }
            if (changed) {
                prefs.edit().putString("list", arr.toString()).apply();
                sendBroadcast(new Intent(LockService.ACTION_HABITS_UPDATED).setPackage(getPackageName()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(habitsUpdatedReceiver); } catch (Exception ignored) {}
    }
}
