package com.stricthabits.app;

import java.io.Serializable;

public class Habit implements Serializable {
    public String name;
    public String time;
    public boolean telegramOnly;
    public boolean soundEnabled;
    public boolean completedToday;
    public int hour;
    public int minute;

    public Habit(String name, String time, boolean telegramOnly, boolean soundEnabled) {
        this.name = name;
        this.time = time;
        this.telegramOnly = telegramOnly;
        this.soundEnabled = soundEnabled;
        this.completedToday = false;
        String[] parts = time.split(":");
        if (parts.length == 2) {
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        }
    }
}