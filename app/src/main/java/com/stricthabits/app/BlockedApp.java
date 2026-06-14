package com.stricthabits.app;

import java.io.Serializable;

public class BlockedApp implements Serializable {
    private String packageName;
    private String appName;
    private boolean enabled;
    private String blockType; // "permanent", "time_based"
    private String startTime; // "HH:mm" (for time_based)
    private String endTime;   // "HH:mm" (for time_based)

    public BlockedApp(String packageName, String appName, String blockType) {
        this.packageName = packageName;
        this.appName = appName;
        this.blockType = blockType;
        this.enabled = true;
        this.startTime = "00:00";
        this.endTime = "23:59";
    }

    // Getters
    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public boolean isEnabled() { return enabled; }
    public String getBlockType() { return blockType; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }

    // Setters
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setBlockType(String blockType) { this.blockType = blockType; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isBlockedNow() {
        if (!enabled) return false;

        if ("permanent".equals(blockType)) {
            return true;
        } else if ("time_based".equals(blockType)) {
            java.util.Calendar now = java.util.Calendar.getInstance();
            String currentTime = String.format("%02d:%02d",
                    now.get(java.util.Calendar.HOUR_OF_DAY),
                    now.get(java.util.Calendar.MINUTE));

            String[] start = startTime.split(":");
            String[] end = endTime.split(":");
            String[] current = currentTime.split(":");

            int startMin = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
            int endMin = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);
            int curMin = Integer.parseInt(current[0]) * 60 + Integer.parseInt(current[1]);

            if (startMin <= endMin) {
                return curMin >= startMin && curMin <= endMin;
            } else {
                return curMin >= startMin || curMin <= endMin;
            }
        }
        return false;
    }
}
