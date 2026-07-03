package com.stricthabits.app;

import java.util.Map;

public class TimerBlock {
    private String name;
    private String startTime;
    private String endTime;
    private Map<String, Boolean> days;
    private boolean enabled;

    public TimerBlock(String name, String startTime, String endTime, Map<String, Boolean> days) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.days = days;
        this.enabled = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Map<String, Boolean> getDays() {
        return days;
    }

    public void setDays(Map<String, Boolean> days) {
        this.days = days;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
