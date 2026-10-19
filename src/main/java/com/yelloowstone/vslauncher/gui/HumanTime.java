package com.yelloowstone.vslauncher.gui;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.yelloowstone.vslauncher.Settings;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.util.Callback;

/** Renders timestamps as "5 min ago" / "6 days ago". */
public class HumanTime {

    private static final DateTimeFormatter ABSOLUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private static Settings settings = new Settings();

    public static void configure(final Settings value) {
        settings = value;
    }

    private static ZoneId zone() {
        try {
            if (settings.timeZone != null && !settings.timeZone.isBlank()) {
                return ZoneId.of(settings.timeZone);
            }
        } catch (RuntimeException e) {
            // unknown zone id in settings.json: use the system zone
        }
        return ZoneId.systemDefault();
    }

    /** Local date and time in the chosen zone, e.g. "2026-10-19 10:45 EDT". */
    public static String absolute(final LocalDateTime time) {
        return time == null ? "" : ABSOLUTE.format(time.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone()));
    }

    /** Honours the "relative times" setting: "5 min ago" or the date. */
    public static String format(final LocalDateTime time) {
        if (time == null || time.getYear() < 1971) { // a missing file reports the epoch
            return "never";
        }
        return settings.relativeTimes ? relative(time, LocalDateTime.now()) : absolute(time);
    }

    /** The date, plus the relative form when that setting is on, e.g. "2026-10-19 10:45 EDT (2 hours ago)". */
    public static String describe(final LocalDateTime time) {
        if (time == null || time.getYear() < 1971) {
            return "never";
        }
        return settings.relativeTimes ? absolute(time) + " (" + relative(time, LocalDateTime.now()) + ")" : absolute(time);
    }

    /** Converts an ISO-8601 instant such as "2026-10-19T14:45:45Z" to local time; null if unparseable. */
    public static LocalDateTime parseInstant(final String text) {
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault());
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    static String relative(final LocalDateTime time, final LocalDateTime now) {
        if (time == null || time.getYear() < 1971) { // a missing file reports the epoch
            return "never";
        }
        final long seconds = Duration.between(time, now).getSeconds();
        if (seconds < 0) {
            return absolute(time); // in the future (clock skew): show the exact time
        }
        if (seconds < 60) {
            return "just now";
        }
        final long minutes = seconds / 60;
        if (minutes < 60) {
            return unit(minutes, "min");
        }
        final long hours = minutes / 60;
        if (hours < 24) {
            return unit(hours, "hour");
        }
        final long days = hours / 24;
        if (days < 30) {
            return unit(days, "day");
        }
        if (days < 365) {
            return unit(days / 30, "month");
        }
        return unit(days / 365, "year");
    }

    private static String unit(final long amount, final String name) {
        return amount + " " + name + (amount == 1 || name.equals("min") ? "" : "s") + " ago";
    }

    /**
     * Cell factory for a LocalDateTime column: the value stays a LocalDateTime so sorting is
     * chronological, only the rendering is relative. The exact time is in the tooltip.
     */
    public static <S> Callback<TableColumn<S, LocalDateTime>, TableCell<S, LocalDateTime>> cellFactory() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(final LocalDateTime time, final boolean empty) {
                super.updateItem(time, empty);
                if (empty || time == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(format(time));
                    setTooltip(new Tooltip(absolute(time)));
                }
            }
        };
    }
}
