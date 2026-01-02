package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.VintageStoryInstance;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MapInfoPage {
	
	public static Button createBackButton(final Context context, final VintageStoryInstance instance) {
		final Button button = new Button("Back");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			MapsInfoPage.create(context, instance);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}
	
	public static void create(final Context context, final VintageStoryInstance instance, final VintageStoryMap map) {
		context.getRootNode().getChildren().clear();
		context.getRootNode().getChildren().addAll(
				createBackButton(context, instance));
	}
}
