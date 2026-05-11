package com.stricthabits.app;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Habit implements Serializable {
    private String name;
    private String time;
    private boolean soundEnabled;
    private boolean enabled;
    private boolean completedToday;
    private String lastCompletedDate; // format: "yyyy-MM-dd"
    private String skippedDate; // format: "yyyy-MM-dd"
    private Map<String, Boolean> days; // key: "mon","tue",...

    public Habit(String name, String time, boolean soundEnabled, Map<String, Boolean> days) {
        this.name = name;
        this.time = time;
        this.soundEnabled = soundEnabled;
        this.enabled = true;
        this.days = days;
        this.completedToday = false;
        this.lastCompletedDate = "";
        this.skippedDate = "";
    }

    // Геттеры
    public String getName() { return name; }
    public String getTime() { return time; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public boolean isEnabled() { return enabled; }
    public boolean isCompletedToday() { return completedToday; }
    public String getLastCompletedDate() { return lastCompletedDate; }
    public String getSkippedDate() { return skippedDate; }
    public Map<String, Boolean> getDays() { return days; }

    // Сеттеры
    public void setCompletedToday(boolean completed) { this.completedToday = completed; }
    public void setLastCompletedDate(String date) { this.lastCompletedDate = date; }
    public void setSkippedDate(String date) { this.skippedDate = date; }
    public void setSoundEnabled(boolean sound) { this.soundEnabled = sound; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
