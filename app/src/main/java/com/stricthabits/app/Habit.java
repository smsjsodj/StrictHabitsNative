package com.stricthabits.app;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Habit implements Serializable {
    private String name;
    private String time;
    private boolean soundEnabled;
    private boolean completedToday;
    private Map<String, Boolean> days; // key: "mon","tue",...

    public Habit(String name, String time, boolean soundEnabled, Map<String, Boolean> days) {
        this.name = name;
        this.time = time;
        this.soundEnabled = soundEnabled;
        this.days = days;
        this.completedToday = false;
    }

    // Геттеры
    public String getName() { return name; }
    public String getTime() { return time; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public boolean isCompletedToday() { return completedToday; }
    public Map<String, Boolean> getDays() { return days; }

    // Сеттеры
    public void setCompletedToday(boolean completed) { this.completedToday = completed; }
    public void setSoundEnabled(boolean sound) { this.soundEnabled = sound; }
}