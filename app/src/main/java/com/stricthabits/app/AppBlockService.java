package com.stricthabits.app;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

public class AppBlockService extends Service {
    private static final String TAG = "AppBlockService";
    private UsageStatsManager usageStatsManager;
    private Handler handler;
    private final long CHECK_INTERVAL = 1000; // Check every second
    private Runnable checkRunnable;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        usageStatsManager = (UsageStatsManager) getSystemService(Service.USAGE_STATS_SERVICE);
        prefs = getSharedPreferences("habits", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startMonitoring();
        return START_STICKY;
    }

    private void startMonitoring() {
        if (checkRunnable == null) {
            checkRunnable = new Runnable() {
                @Override
                public void run() {
                    String currentPackage = getCurrentForegroundApp();
                    if (currentPackage != null && isAppBlocked(currentPackage)) {
                        showBlockDialog(currentPackage);
                    }
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            };
            handler.post(checkRunnable);
        }
    }

    private String getCurrentForegroundApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                UsageEvents events = usageStatsManager.queryEvents(
                        System.currentTimeMillis() - 1000,
                        System.currentTimeMillis()
                );

                UsageEvents.Event event = new UsageEvents.Event();
                String lastPackage = null;
                while (events.getNextEvent(event)) {
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastPackage = event.getPackageName();
                    }
                }
                return lastPackage;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private boolean isAppBlocked(String packageName) {
        // Check if app is in whitelist first
        if (isAppWhitelisted(packageName)) {
            return false;
        }

        try {
            String blockedAppsJson = prefs.getString("blocked_apps", "[]");
            JSONArray blockedApps = new JSONArray(blockedAppsJson);

            for (int i = 0; i < blockedApps.length(); i++) {
                JSONObject app = blockedApps.getJSONObject(i);
                if (app.getString("packageName").equals(packageName)
                        && app.getBoolean("enabled")) {

                    BlockedApp blockedApp = jsonToBlockedApp(app);
                    if (blockedApp.isBlockedNow()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isAppWhitelisted(String packageName) {
        try {
            String whitelistedAppsJson = prefs.getString("whitelisted_apps", "[]");
            JSONArray whitelistedApps = new JSONArray(whitelistedAppsJson);

            for (int i = 0; i < whitelistedApps.length(); i++) {
                JSONObject app = whitelistedApps.getJSONObject(i);
                if (app.getString("packageName").equals(packageName)
                        && app.getBoolean("enabled")) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private BlockedApp jsonToBlockedApp(JSONObject json) {
        try {
            BlockedApp app = new BlockedApp(
                    json.getString("packageName"),
                    json.getString("appName"),
                    json.getString("blockType")
            );
            app.setStartTime(json.optString("startTime", "00:00"));
            app.setEndTime(json.optString("endTime", "23:59"));
            app.setEnabled(json.getBoolean("enabled"));
            return app;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showBlockDialog(String packageName) {
        Intent intent = new Intent(this, BlockDialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("package_name", packageName);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
