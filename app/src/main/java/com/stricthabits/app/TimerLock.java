package com.stricthabits.app;

import java.io.Serializable;

public class TimerLock implements Serializable {
    private String name;
    private int durationMinutes;
    private boolean enabled;

    public TimerLock(String name, int durationMinutes) {
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.enabled = true;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
