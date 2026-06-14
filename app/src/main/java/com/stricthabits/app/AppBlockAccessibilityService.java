package com.stricthabits.app;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONArray;
import org.json.JSONObject;

public class AppBlockAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppBlockAccessibilityService";
    private SharedPreferences prefs;
    private long lastBlockTime = 0;
    private static final long BLOCK_COOLDOWN = 500; // ms

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("habits", MODE_PRIVATE);
        Log.d(TAG, "AppBlockAccessibilityService CREATED");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "AppBlockAccessibilityService CONNECTED");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
            
            Log.d(TAG, "Window changed: " + packageName);
            
            if (packageName != null && !packageName.equals(getPackageName())) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBlockTime > BLOCK_COOLDOWN) {
                    Log.d(TAG, "Checking if blocked: " + packageName);
                    if (isAppBlocked(packageName)) {
                        Log.d(TAG, "App is BLOCKED, blocking now: " + packageName);
                        lastBlockTime = currentTime;
                        blockApp(packageName);
                    } else {
                        Log.d(TAG, "App is NOT blocked: " + packageName);
                    }
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    private boolean isAppBlocked(String packageName) {
        // Проверяем белый список первым
        if (isAppWhitelisted(packageName)) {
            Log.d(TAG, "App is WHITELISTED: " + packageName);
            return false;
        }

        try {
            String blockedAppsJson = prefs.getString("blocked_apps", "[]");
            JSONArray blockedApps = new JSONArray(blockedAppsJson);
            Log.d(TAG, "Total blocked apps: " + blockedApps.length());

            for (int i = 0; i < blockedApps.length(); i++) {
                JSONObject app = blockedApps.getJSONObject(i);
                String blockPackageName = app.getString("packageName");
                boolean isEnabled = app.getBoolean("enabled");
                
                if (blockPackageName.equals(packageName) && isEnabled) {
                    BlockedApp blockedApp = jsonToBlockedApp(app);
                    boolean blockedNow = blockedApp.isBlockedNow();
                    Log.d(TAG, "Found blocked app: " + packageName + ", blockedNow=" + blockedNow);
                    if (blockedNow) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if app blocked", e);
        }
        return false;
    }

    private boolean isAppWhitelisted(String packageName) {
        try {
            String whitelistedAppsJson = prefs.getString("whitelisted_apps", "[]");
            JSONArray whitelistedApps = new JSONArray(whitelistedAppsJson);
            Log.d(TAG, "Total whitelisted apps: " + whitelistedApps.length());

            for (int i = 0; i < whitelistedApps.length(); i++) {
                JSONObject app = whitelistedApps.getJSONObject(i);
                String whitePackageName = app.getString("packageName");
                boolean isEnabled = app.getBoolean("enabled");
                
                if (whitePackageName.equals(packageName) && isEnabled) {
                    Log.d(TAG, "App is in whitelist: " + packageName);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking whitelist", e);
        }
        return false;
    }

    private void blockApp(String packageName) {
        try {
            Log.d(TAG, "Starting to block app: " + packageName);
            
            // Используем overlay сервис вместо Activity
            android.content.Intent overlayIntent = new android.content.Intent(this, LockOverlayService.class);
            overlayIntent.putExtra("package_name", packageName);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent);
            } else {
                startService(overlayIntent);
            }
            
            Log.d(TAG, "LockOverlayService started for: " + packageName);
            
        } catch (Exception e) {
            Log.e(TAG, "Error blocking app", e);
            e.printStackTrace();
        }
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
}
