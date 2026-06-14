package com.stricthabits.app;

import android.app.Activity;
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

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

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
