package com.yelloowstone.vslauncher.gui;

import java.util.Map;

import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

/** Buttons with a small vector icon (Material Design paths) chosen from the button's text. */
public class Icons {
    private static final int SIZE = 16;

    private static final String FOLDER = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final String PLAY = "M8 5v14l11-7z";
    private static final String BACK = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";
    private static final String ADD = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    private static final String REFRESH = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    private static final String DOWNLOAD = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
    private static final String UPGRADE = "M16 18v2H8v-2h8zM11 7.99V16h2V7.99h3L12 4 8 7.99h3z";
    private static final String SETTINGS = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    private static final String CLOSE = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
    private static final String CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    private static final String COPY = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z";
    private static final String MORE = "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z";
    private static final String EXTENSION = "M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z";
    private static final String MAP = "M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z";
    private static final String MERGE = "M17 20.41L18.41 19 15 15.59 13.59 17 17 20.41zM7.5 8H11v5.59L5.59 19 7 20.41l6-6V8h3.5L12 3.5 7.5 8z";

    private static final Map<String, String> BY_TEXT = Map.ofEntries(
            Map.entry("Open", PLAY),
            Map.entry("Open Runtime Path", FOLDER),
            Map.entry("Open Data Path", FOLDER),
            Map.entry("Open Mod Path", FOLDER),
            Map.entry("Open Config", FOLDER),
            Map.entry("Open Maps", MAP),
            Map.entry("Manage Mods", EXTENSION),
            Map.entry("Back", BACK),
            Map.entry("Add", ADD),
            Map.entry("Create New Instance", ADD),
            Map.entry("Check for Updates", REFRESH),
            Map.entry("Export Manifest", DOWNLOAD),
            Map.entry("Update", UPGRADE),
            Map.entry("Update All", UPGRADE),
            Map.entry("Settings", SETTINGS),
            Map.entry("Exit", CLOSE),
            Map.entry("Submit", CHECK),
            Map.entry("Go", PLAY),
            Map.entry("Fit", REFRESH),
            Map.entry("Save Name", CHECK),
            Map.entry("Copy Instance Data", COPY),
            Map.entry("Copy Mods", COPY),
            Map.entry("Merge Maps", MERGE),
            Map.entry("Merge", MERGE),
            Map.entry("More", MORE));

    /** A button showing {@code text}, with the icon registered for that text if there is one. */
    public static Button button(final String text) {
        final Button button = new Button(text);
        final String path = text.startsWith("Load Last Instance") ? PLAY : BY_TEXT.get(text);
        if (path != null) {
            // -fx-shape stretches the path's bounding box to the region, so size the region to the
            // path's own proportions (scaled from the 24x24 design grid) or narrow icons get distorted.
            final SVGPath shape = new SVGPath();
            shape.setContent(path);
            final double scale = SIZE / 24.0;
            final double width = shape.getLayoutBounds().getWidth() * scale;
            final double height = shape.getLayoutBounds().getHeight() * scale;
            final Region icon = new Region();
            icon.setMinSize(width, height);
            icon.setPrefSize(width, height);
            icon.setMaxSize(width, height);
            icon.setStyle("-fx-shape: \"" + path + "\"; -fx-background-color: white;");
            button.setGraphic(icon);
            button.setGraphicTextGap(8);
        }
        return button;
    }
}
