package com.stricthabits.app;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONArray;
import org.json.JSONObject;

public class AppBlockAccessibilityService extends AccessibilityService {
    private SharedPreferences prefs;
    private long lastBlockTime = 0;
    private static final long BLOCK_COOLDOWN = 500; // ms

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("habits", MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
            
            if (packageName != null && !packageName.equals(getPackageName())) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBlockTime > BLOCK_COOLDOWN) {
                    if (isAppBlocked(packageName)) {
                        lastBlockTime = currentTime;
                        blockApp(packageName);
                    }
                }
            }
        }
    }

    @Override
    public void onInterrupt() {}

    private boolean isAppBlocked(String packageName) {
        // Проверяем белый список первым
        if (isAppWhitelisted(packageName)) {
            return false;
        }

        try {
            String blockedAppsJson = prefs.getString("blocked_apps", "[]");
            JSONArray blockedApps = new JSONArray(blockedAppsJson);

            for (int i = 0; i < blockedApps.length(); i++) {
                JSONObject app = blockedApps.getJSONObject(i);
                if (app.getString("packageName").equals(packageName) && app.getBoolean("enabled")) {
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
                if (app.getString("packageName").equals(packageName) && app.getBoolean("enabled")) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void blockApp(String packageName) {
        try {
            android.content.Intent intent = new android.content.Intent(this, BlockDialogActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("package_name", packageName);
            startActivity(intent);
            
            // Возвращаемся в главное приложение
            android.content.Intent homeIntent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            homeIntent.addCategory(android.content.Intent.CATEGORY_HOME);
            homeIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        } catch (Exception e) {
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
