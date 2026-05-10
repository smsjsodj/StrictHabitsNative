package com.stricthabits.app;

import java.io.Serializable;

public class Habit implements Serializable {
    private String name;
    private String time;
    private boolean soundEnabled;
    private int hour, minute;

    public Habit(String name, String time, boolean soundEnabled) {
        this.name = name;
        this.time = time;
        this.soundEnabled = soundEnabled;
        String[] parts = time.split(":");
        if (parts.length == 2) {
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        }
    }

    public String getName() { return name; }
    public String getTime() { return time; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }
}