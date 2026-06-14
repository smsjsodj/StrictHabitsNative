package com.stricthabits.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Button;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.view.View;

public class BlockDialogActivity extends Activity {
    private String packageName;
    private PackageManager packageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_block_app);

        // Флаги для отображения поверх всех экранов
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        
        // Для Android 10+ нужен этот флаг для отображения поверх других приложений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            params.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        
        getWindow().setAttributes(params);
        getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);

        packageManager = getPackageManager();
        packageName = getIntent().getStringExtra("package_name");

        setupUI();

        findViewById(R.id.btnOkBlock).setOnClickListener(v -> finish());
    }

    private void setupUI() {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            Drawable appIcon = packageManager.getApplicationIcon(packageName);

            TextView titleView = findViewById(R.id.blockTitle);
            ImageView iconView = findViewById(R.id.blockAppIcon);
            TextView messageView = findViewById(R.id.blockMessage);

            titleView.setText("Приложение заблокировано");
            messageView.setText("Приложение \"" + appName + "\" сейчас заблокировано.");
            iconView.setImageDrawable(appIcon);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent going back
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}
