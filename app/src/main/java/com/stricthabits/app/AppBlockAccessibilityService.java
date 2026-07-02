package com.stricthabits.app;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class AppBlockAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppBlockAccessibilityService";
    private SharedPreferences prefs;
    // Отдельный cooldown для каждого приложения
    private Map<String, Long> lastBlockTimeByApp = new HashMap<>();
    private static final long BLOCK_COOLDOWN = 2000; // ms (2 секунды между блокировками одного приложения)

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            prefs = getSharedPreferences("habits", MODE_PRIVATE);
            Log.d(TAG, "AppBlockAccessibilityService CREATED");
        } catch (Exception e) {
            Log.e(TAG, "Error creating SharedPreferences", e);
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "AppBlockAccessibilityService CONNECTED");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event == null) return;

            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
            int eventType = event.getEventType();

            Log.d(TAG, "Accessibility Event - Type: " + eventType + ", Package: " + packageName);

            // Обрабатываем несколько типов событий для перехвата приложений
            if ((eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                 eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
                packageName != null && !packageName.equals(getPackageName())) {

                long currentTime = System.currentTimeMillis();
                long lastBlockTime = lastBlockTimeByApp.getOrDefault(packageName, 0L);

                if (currentTime - lastBlockTime > BLOCK_COOLDOWN) {
                    Log.d(TAG, "Checking if blocked: " + packageName + " (last block was " + (currentTime - lastBlockTime) + "ms ago)");
                    if (isAppBlocked(packageName)) {
                        Log.d(TAG, "App is BLOCKED, blocking now: " + packageName);
                        lastBlockTimeByApp.put(packageName, currentTime);
                        blockApp(packageName);
                    } else {
                        Log.d(TAG, "App is NOT blocked: " + packageName);
                    }
                } else {
                    Log.d(TAG, "Cooldown not finished for " + packageName + " (need " + (BLOCK_COOLDOWN - (currentTime - lastBlockTime)) + "ms more)");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onAccessibilityEvent", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    private boolean isAppBlocked(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty()) {
                return false;
            }

            // Проверяем белый список первым
            if (isAppWhitelisted(packageName)) {
                Log.d(TAG, "App is WHITELISTED: " + packageName);
                return false;
            }

            if (prefs == null) {
                Log.e(TAG, "SharedPreferences is null");
                return false;
            }

            String blockedAppsJson = prefs.getString("blocked_apps", "[]");
            if (blockedAppsJson == null || blockedAppsJson.isEmpty()) {
                blockedAppsJson = "[]";
            }

            JSONArray blockedApps = new JSONArray(blockedAppsJson);
            Log.d(TAG, "Total blocked apps: " + blockedApps.length());

            for (int i = 0; i < blockedApps.length(); i++) {
                try {
                    JSONObject app = blockedApps.getJSONObject(i);
                    String blockPackageName = app.optString("packageName", "");
                    boolean isEnabled = app.optBoolean("enabled", true);

                    if (blockPackageName.equals(packageName) && isEnabled) {
                        BlockedApp blockedApp = jsonToBlockedApp(app);
                        if (blockedApp != null) {
                            boolean blockedNow = blockedApp.isBlockedNow();
                            Log.d(TAG, "Found blocked app: " + packageName + ", blockedNow=" + blockedNow);
                            if (blockedNow) {
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing blocked app at index " + i, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if app blocked", e);
        }
        return false;
    }

    private boolean isAppWhitelisted(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty()) {
                return false;
            }

            if (prefs == null) {
                Log.e(TAG, "SharedPreferences is null in isAppWhitelisted");
                return false;
            }

            String whitelistedAppsJson = prefs.getString("whitelisted_apps", "[]");
            if (whitelistedAppsJson == null || whitelistedAppsJson.isEmpty()) {
                whitelistedAppsJson = "[]";
            }

            JSONArray whitelistedApps = new JSONArray(whitelistedAppsJson);
            Log.d(TAG, "Total whitelisted apps: " + whitelistedApps.length());

            for (int i = 0; i < whitelistedApps.length(); i++) {
                try {
                    JSONObject app = whitelistedApps.getJSONObject(i);
                    String whitePackageName = app.optString("packageName", "");
                    boolean isEnabled = app.optBoolean("enabled", true);

                    if (whitePackageName.equals(packageName) && isEnabled) {
                        Log.d(TAG, "App is in whitelist: " + packageName);
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing whitelisted app at index " + i, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking whitelist", e);
        }
        return false;
    }

    private void blockApp(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty()) {
                Log.e(TAG, "Cannot block app with null/empty package name");
                return;
            }

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
            Log.e(TAG, "Error blocking app: " + packageName, e);
        }
    }

    private BlockedApp jsonToBlockedApp(JSONObject json) {
        try {
            if (json == null) {
                return null;
            }

            String packageName = json.optString("packageName", "");
            String appName = json.optString("appName", "Unknown");
            String blockType = json.optString("blockType", "permanent");

            if (packageName.isEmpty()) {
                Log.e(TAG, "Empty package name in blocked app JSON");
                return null;
            }

            BlockedApp app = new BlockedApp(packageName, appName, blockType);
            app.setStartTime(json.optString("startTime", "00:00"));
            app.setEndTime(json.optString("endTime", "23:59"));
            app.setEnabled(json.optBoolean("enabled", true));
            return app;
        } catch (Exception e) {
            Log.e(TAG, "Error converting JSON to BlockedApp", e);
            return null;
        }
    }
}
