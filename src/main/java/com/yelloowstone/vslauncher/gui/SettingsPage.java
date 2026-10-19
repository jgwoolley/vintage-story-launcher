package com.yelloowstone.vslauncher.gui;

import java.io.IOException;
import java.time.ZoneId;
import java.util.TreeSet;
import java.time.LocalDateTime;
import java.util.Properties;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class SettingsPage {
    private static Button createOpenConfigButton(final Context context) {
        final Button button = Icons.button("Open Config");
        button.setOnAction(e -> {
        	context.getVolumeProperty().play();
        	Context.openFile(context.getConfigHome());

        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }
	
    private static Properties getProperties() {
    	try {
        	Properties properties = new Properties();
        	properties.load(SettingsPage.class.getResourceAsStream("/vintagestory.properties"));
			return properties;

		} catch (Exception e) {
			e.printStackTrace();
		}
    	
    	return null;
    }
    
    private static Label createVersionLabel(final Properties properties, final String key, final String text) {
    	String version = "Unknown";
    	if(properties != null) {
    		try {
    			version = properties.getProperty(key);
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    	}
    	
    	return new Label(text + ": " + version);
    }
    
    /** The build time is stamped in UTC at build; show it in local time. */
    private static Label createBuildTimeLabel(final Properties properties) {
        final String raw = properties == null ? null : properties.getProperty("build.time");
        final LocalDateTime local = HumanTime.parseInstant(raw);
        if (local == null) {
            return new Label("Build Time: " + (raw == null ? "Unknown" : raw));
        }
        return new Label("Build Time: " + HumanTime.describe(local));
    }

    private static HBox createVolumeForm(final Context context) {
        final Label volumeLabel = new Label("Volume");
        final Slider slider = new Slider(0, 1.0, 1.0);
        slider.valueProperty().bindBidirectional(context.getVolumeProperty().getProperty());

    	 // 1. Allow the ComboBox to grow and fill available space
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.setMaxWidth(Double.MAX_VALUE);

    	final HBox volumeForm = new HBox(10);
        volumeForm.getChildren().addAll(volumeLabel, slider);

        // 2. Set the HBox to take up half the width of its parent
        // Note: This works best if the parent is a VBox or similar layout
    	volumeForm.prefWidthProperty().bind(context.getStage().widthProperty().divide(2));
    	
    	return volumeForm;
    }
    
    private static final String SYSTEM_ZONE = "System default";

    private static HBox createTimeZoneForm(final Context context) {
        final ComboBox<String> zones = new ComboBox<>();
        zones.getItems().add(SYSTEM_ZONE);
        zones.getItems().addAll(new TreeSet<>(ZoneId.getAvailableZoneIds()));
        final String current = context.getSettings().timeZone;
        zones.setValue(current == null || !zones.getItems().contains(current) ? SYSTEM_ZONE : current);
        zones.valueProperty().addListener((obs, old, value) -> {
            context.getSettings().timeZone = SYSTEM_ZONE.equals(value) ? null : value;
            context.saveSettings();
        });
        HBox.setHgrow(zones, Priority.ALWAYS);
        zones.setMaxWidth(Double.MAX_VALUE);
        return new HBox(10, new Label("Time zone"), zones);
    }

    private static CheckBox createRelativeTimesBox(final Context context) {
        final CheckBox box = new CheckBox("Show times as \"5 min ago\" instead of the date");
        box.setStyle("-fx-text-fill: white;");
        box.setSelected(context.getSettings().relativeTimes);
        box.selectedProperty().addListener((obs, old, value) -> {
            context.getSettings().relativeTimes = value;
            context.saveSettings();
        });
        return box;
    }

    private static CheckBox createCloseToTrayBox(final Context context) {
        final CheckBox box = new CheckBox("Keep running in the system tray when the window is closed");
        box.setStyle("-fx-text-fill: white;");
        if (!TrayMenu.isSupported()) {
            box.setDisable(true);
            box.setText(box.getText() + " (no system tray available)");
        }
        box.setSelected(context.getSettings().closeToTray);
        box.selectedProperty().addListener((obs, old, value) -> {
            context.getSettings().closeToTray = value;
            context.saveSettings();
        });
        return box;
    }

    private static HBox createScrollSpeedForm(final Context context) {
        final Slider slider = new Slider(0.25, 4.0, context.getSettings().scrollSpeed);
        final Label value = new Label();
        value.textProperty().bind(Bindings.format("x%.2f", slider.valueProperty()));
        slider.valueProperty().addListener((obs, old, speed) -> {
            context.getSettings().scrollSpeed = speed.doubleValue();
            if (!slider.isValueChanging()) {
                context.saveSettings();
            }
        });
        slider.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) {
                context.saveSettings();
            }
        });
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.setMaxWidth(Double.MAX_VALUE);
        return new HBox(10, new Label("Scroll speed"), slider, value);
    }

	public static void create(final Context context) {
		final Properties properties = getProperties() ;
		
		final Label versionLabel = createVersionLabel(properties, "version", "Version");
		final Label buildTimeLabel = createBuildTimeLabel(properties);
        final Button openConfigButton = createOpenConfigButton(context);
        final Button backButton = BackButton.create(context);
        
        final HBox volumeForm = createVolumeForm(context);
        
        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(versionLabel, buildTimeLabel, volumeForm,
        		createScrollSpeedForm(context), createTimeZoneForm(context), createRelativeTimesBox(context), createCloseToTrayBox(context),
        		openConfigButton, backButton);
    }
}
