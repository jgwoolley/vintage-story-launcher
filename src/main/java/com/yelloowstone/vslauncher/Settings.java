package com.yelloowstone.vslauncher;

import java.io.File;

import tools.jackson.databind.ObjectMapper;

/** User preferences, stored in config home as settings.json. */
public class Settings {
    /** Time zone id such as "America/New_York"; null means the system's. */
    public String timeZone;
    /** Show times as "5 min ago" rather than the date. */
    public boolean relativeTimes = true;
    /** Closing the window hides the launcher to the system tray instead of exiting (when there is a tray). */
    public boolean closeToTray = false;
    /** Multiplier applied to mouse wheel / trackpad scrolling. */
    public double scrollSpeed = 1.0;

    public Settings() {
    }

    public static Settings load(final File file, final ObjectMapper mapper) {
        try {
            if (file.isFile()) {
                final Settings settings = mapper.readValue(file, Settings.class);
                settings.scrollSpeed = Math.max(0.1, Math.min(10, settings.scrollSpeed));
                return settings;
            }
        } catch (RuntimeException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }
        return new Settings();
    }

    public void save(final File file, final ObjectMapper mapper) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, this);
        } catch (RuntimeException e) {
            System.err.println("Could not write " + file + ": " + e.getMessage());
        }
    }
}
