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
    private static Button createOpenConfigButton(final Context context) {
        final Button button = new Button("Open Config");
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
    
	public static void create(final Context context) {
		final Properties properties = getProperties() ;
		
		final Label versionLabel = createVersionLabel(properties, "version", "Version");
		final Label buildTimeLabel = createVersionLabel(properties, "build.time", "Build Time");
        final Button openConfigButton = createOpenConfigButton(context);
        final Button backButton = BackButton.create(context);
        
        final HBox volumeForm = createVolumeForm(context);
        
        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(versionLabel, buildTimeLabel, volumeForm, openConfigButton, backButton);
    }
}
