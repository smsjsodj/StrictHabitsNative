package com.stricthabits.app;

import java.io.Serializable;

public class WhitelistedApp implements Serializable {
    private String packageName;
    private String appName;
    private boolean enabled;

    public WhitelistedApp(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
        this.enabled = true;
    }

    // Getters
    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public boolean isEnabled() { return enabled; }

    // Setters
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
