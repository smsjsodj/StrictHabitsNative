package com.stricthabits.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import androidx.core.app.NotificationCompat;

public class LockOverlayService extends Service {
    private static final String TAG = "LockOverlayService";
    private static final String CHANNEL_ID = "lock_overlay_channel";
    private static final int NOTIFICATION_ID = 1001;
    private WindowManager windowManager;
    private View overlayView;
    private String lockedPackageName;
    private WindowManager.LayoutParams params;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Log.d(TAG, "LockOverlayService created");
        
        // Создаём notification channel для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Block Lock",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called, startId=" + startId);
        
        if (intent != null) {
            lockedPackageName = intent.getStringExtra("package_name");
            Log.d(TAG, "Showing overlay for: " + lockedPackageName);
            
            // Показываем notification для foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("App is blocked")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setOngoing(true)
                        .build();
                startForeground(NOTIFICATION_ID, notification);
                Log.d(TAG, "Started as foreground service");
            }
            
            showLockOverlay(lockedPackageName);
        } else {
            Log.d(TAG, "Intent is null");
        }
        return START_STICKY;
    }

    private void showLockOverlay(String packageName) {
        try {
            Log.d(TAG, "showLockOverlay called for: " + packageName);
            
            // Удаляем старый overlay если он был
            if (overlayView != null && overlayView.getParent() != null) {
                try {
                    windowManager.removeView(overlayView);
                    Log.d(TAG, "Removed old overlay view");
                } catch (Exception e) {
                    Log.e(TAG, "Error removing old view", e);
                }
                overlayView = null;
            }

            // Создаём layout
            LayoutInflater inflater = LayoutInflater.from(this);
            overlayView = inflater.inflate(R.layout.dialog_block_app, null);
            Log.d(TAG, "Layout inflated");

            // Параметры для отображения поверх всех приложений
            params = new WindowManager.LayoutParams();
            params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
            params.format = PixelFormat.TRANSLUCENT;
            params.flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.CENTER;

            Log.d(TAG, "LayoutParams configured");

            // Устанавливаем информацию о приложении
            setupUI(overlayView, packageName);

            // Кнопка OK - должна быть кликабельной
            Button btnOk = overlayView.findViewById(R.id.btnOkBlock);
            if (btnOk != null) {
                btnOk.setOnClickListener(v -> {
                    Log.d(TAG, "OK button clicked");
                    hideOverlay();
                });
                Log.d(TAG, "OK button listener set");
            } else {
                Log.e(TAG, "btnOkBlock not found in layout!");
            }

            // Добавляем view в windowManager
            windowManager.addView(overlayView, params);
            Log.d(TAG, "Overlay displayed successfully for package: " + packageName);

        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay", e);
            e.printStackTrace();
        }
    }

    private void hideOverlay() {
        try {
            Log.d(TAG, "hideOverlay called");
            
            if (overlayView != null && overlayView.getParent() != null) {
                windowManager.removeView(overlayView);
                Log.d(TAG, "Overlay view removed");
            }
            
            // Возвращаемся на главный экран
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(homeIntent);
            
            Log.d(TAG, "Returned to home screen, service still active for future blocks");
            
            // НЕ останавливаем сервис - он может понадобиться снова
            // stopSelf();
            
        } catch (Exception e) {
            Log.e(TAG, "Error hiding overlay", e);
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
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing view in onDestroy", e);
            }
            overlayView = null;
        }
        Log.d(TAG, "LockOverlayService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

