package com.stricthabits.app;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class LockOverlayService extends Service {
    private static final String TAG = "LockOverlayService";
    private WindowManager windowManager;
    private View overlayView;
    private String lockedPackageName;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Log.d(TAG, "LockOverlayService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            lockedPackageName = intent.getStringExtra("package_name");
            Log.d(TAG, "Showing overlay for: " + lockedPackageName);
            showLockOverlay(lockedPackageName);
        }
        return START_NOT_STICKY;
    }

    private void showLockOverlay(String packageName) {
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView);
            }

            // Создаём layout
            LayoutInflater inflater = LayoutInflater.from(this);
            overlayView = inflater.inflate(R.layout.dialog_block_app, null);

            // Параметры для отображения поверх всех приложений
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
            params.format = PixelFormat.TRANSLUCENT;
            params.flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.CENTER;

            // Устанавливаем информацию о приложении
            setupUI(overlayView, packageName);

            // Кнопка OK
            Button btnOk = overlayView.findViewById(R.id.btnOkBlock);
            btnOk.setOnClickListener(v -> {
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                
                if (overlayView != null && overlayView.getParent() != null) {
                    windowManager.removeView(overlayView);
                    overlayView = null;
                }
                
                // Возвращаемся на главный экран
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(homeIntent);
                
                stopSelf();
            });

            // Делаем кнопку активной
            btnOk.setFocusable(true);
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

            windowManager.addView(overlayView, params);
            Log.d(TAG, "Overlay displayed for package: " + packageName);

        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay", e);
        }
    }

    private void setupUI(View view, String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();

            TextView titleView = view.findViewById(R.id.blockTitle);
            ImageView iconView = view.findViewById(R.id.blockAppIcon);
            TextView messageView = view.findViewById(R.id.blockMessage);

            titleView.setText("Приложение заблокировано");
            messageView.setText("Приложение \"" + appName + "\" сейчас заблокировано.");
            
            try {
                iconView.setImageDrawable(packageManager.getApplicationIcon(packageName));
            } catch (Exception e) {
                Log.d(TAG, "Could not load app icon: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error setting up UI", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && overlayView.getParent() != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        Log.d(TAG, "LockOverlayService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
