package com.stricthabits.app;

import java.io.Serializable;

public class Habit implements Serializable {
    private String name;
    private String time;
    private boolean telegramOnly;
    private boolean soundEnabled;
    private boolean completedToday;
    private int hour;
    private int minute;

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

    // Геттеры
    public String getName() { return name; }
    public String getTime() { return time; }
    public boolean isTelegramOnly() { return telegramOnly; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public boolean isCompletedToday() { return completedToday; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }

    // Сеттеры
    public void setCompletedToday(boolean completed) { this.completedToday = completed; }
    public void setSoundEnabled(boolean sound) { this.soundEnabled = sound; }
}