package com.stricthabits.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnTest = findViewById(R.id.btnAddHabit); // используем существующую кнопку
        btnTest.setText("🔒 ТЕСТ БЛОКИРОВКИ");
        btnTest.setOnClickListener(v -> {
            if (checkOverlayPermission()) {
                startLockScreen();
            } else {
                requestOverlayPermission();
            }
        });

        // Кнопка Telegram пока не нужна, скроем
        findViewById(R.id.btnTelegramSetup).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnRequestOverlay).setVisibility(android.view.View.GONE);
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Разрешите показ поверх других приложений", Toast.LENGTH_LONG).show();
        }
    }

    private void startLockScreen() {
        Intent intent = new Intent(this, LockService.class);
        intent.putExtra("habit_name", "Тестовая привычка");
        intent.putExtra("habit_time", "12:00");
        intent.putExtra("telegram_only", false);
        intent.putExtra("sound_enabled", true);
        startService(intent);
        // Сворачиваем приложение, чтобы показать оверлей
        moveTaskToBack(true);
    }
}