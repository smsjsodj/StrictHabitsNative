package com.stricthabits.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
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
    private TextView tvTimer;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long blockEndMillis = 0;
    private boolean timerMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lock_screen);

        Intent intent = getIntent();
        habitName = intent.getStringExtra("habit_name");
        String habitTime = intent.getStringExtra("habit_time");
        String unlockMode = intent.getStringExtra(LockService.EXTRA_UNLOCK_MODE);
        timerMode = intent.getBooleanExtra("block_timer_mode", false);
        blockEndMillis = intent.getLongExtra("block_end_millis", 0);

        TextView tvName = findViewById(R.id.tvHabitName);
        TextView tvTime = findViewById(R.id.tvTime);
        tvTimer = findViewById(R.id.tvTimer);
        EditText etConfirm = findViewById(R.id.etConfirm);
        Button btnUnlock = findViewById(R.id.btnUnlock);

        tvName.setText(habitName == null ? "" : habitName);
        tvTime.setText(habitTime == null ? "" : habitTime);

        // \u0415\u0441\u043b\u0438 \u0440\u0435\u0436\u0438\u043c \u0442\u0430\u0439\u043c\u0435\u0440\u0430 \u0432\u043a\u043b\u044e\u0447\u0435\u043d, \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u043c \u0442\u0430\u0439\u043c\u0435\u0440
        if (timerMode && blockEndMillis > 0) {
            tvTimer.setVisibility(View.VISIBLE);
            startTimer();
        }

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

    private void startTimer() {
        timerHandler = new Handler();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long remaining = blockEndMillis - now;

                if (remaining <= 0) {
                    tvTimer.setText("00:00:00");
                    finish();
                    return;
                }

                int hours = (int) (remaining / (1000 * 60 * 60));
                int minutes = (int) ((remaining % (1000 * 60 * 60)) / (1000 * 60));
                int seconds = (int) ((remaining % (1000 * 60)) / 1000);

                tvTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
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
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        try { unregisterReceiver(habitsUpdatedReceiver); } catch (Exception ignored) {}
    }
}
