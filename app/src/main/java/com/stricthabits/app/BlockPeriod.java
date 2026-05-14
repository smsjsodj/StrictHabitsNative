package com.stricthabits.app;

import java.io.Serializable;
import java.util.Map;

public class BlockPeriod implements Serializable {
    private String startTime; // "HH:mm"
    private String endTime;   // "HH:mm"
    private Map<String, Boolean> days; // mon..sun
    private boolean enabled;

    public BlockPeriod(String startTime, String endTime, java.util.Map<String, Boolean> days) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.days = days;
        this.enabled = true;
    }

    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public java.util.Map<String, Boolean> getDays() { return days; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
