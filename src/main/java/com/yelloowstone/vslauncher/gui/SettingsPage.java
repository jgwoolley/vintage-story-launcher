package com.yelloowstone.vslauncher.gui;

import java.io.IOException;
import java.util.Properties;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class SettingsPage {
    public static Button createOpenConfigButton(final Context context) {
        final Button button = new Button("Open Config");
        button.setOnAction(e -> {
        	context.getVolumeProperty().play();
        	Context.openFile(context.getConfigHome());

        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }
	
    public static Label createVersionLabel() {
    	String version = "Unknown";
    	try {
        	Properties props = new Properties();
			props.load(SettingsPage.class.getResourceAsStream("/vintagestory.properties"));
			version = props.getProperty("version");

		} catch (Exception e) {
			e.printStackTrace();
		}
    	
    	return new Label("Version: " + version);
    }
    
	public static void create(final Context context) {
		final Label versionLabel = createVersionLabel();
        final Button openConfigButton = createOpenConfigButton(context);
        final Button backButton = BackButton.create(context);
        
        final HBox volumeForm = new HBox(10);
        final Label volumeLabel = new Label("Volume");
        final Slider slider = new Slider(0, 1.0, 1.0);
        
        volumeForm.getChildren().addAll(volumeLabel, slider);

        slider.valueProperty().bindBidirectional(context.getVolumeProperty().getProperty());
        
        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(versionLabel, volumeForm, openConfigButton, backButton);
    }
}
