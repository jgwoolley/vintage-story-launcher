package com.yelloowstone.vslauncher.gui;

import java.util.Map;

import com.yelloowstone.vslauncher.MapCoordinates;
import com.yelloowstone.vslauncher.SaveGameScanner;
import com.yelloowstone.vslauncher.SaveGameScanner.SaveInfo;
import com.yelloowstone.vslauncher.VintageStoryInstance;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MapInfoPage {
	private static final int RENDER_SIZE = 2048;

	public static Button createBackButton(final Context context, final VintageStoryInstance instance) {
		final Button button = Icons.button("Back");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			MapsInfoPage.create(context, instance);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	/** Renders the overview off the UI thread (large maps are hundreds of MB) and hands it to the viewer. */
	private static void loadOverview(final VintageStoryMap map, final MapViewer viewer, final Label status) {
		final Task<VintageStoryMap.Image> task = new Task<>() {
			@Override
			protected VintageStoryMap.Image call() throws Exception {
				return map.render(RENDER_SIZE);
			}
		};
		task.setOnSucceeded(x -> {
			final VintageStoryMap.Image image = task.getValue();
			if (image == null) {
				status.setText("This map has no explored tiles.");
				return;
			}
			viewer.setOverview(image);
			status.setText("Drag to pan, scroll to zoom.");
		});
		task.setOnFailed(x -> status.setText("Could not render map: " + task.getException()));
		final Thread thread = new Thread(task, "vs-map-render");
		thread.setDaemon(true);
		thread.start();
	}

	public static void create(final Context context, final VintageStoryInstance instance, final VintageStoryMap map) {
		final Map<String, SaveInfo> saves = SaveGameScanner.scan(instance.getDataPath());

		final Label fileLabel = new Label("Map File: " + map.getFile().getName());
		final Label sourceLabel = new Label("Source: " + MapsInfoPage.describeSource(context, saves, map));

		final TextField nameField = new TextField(context.getMapRegistry().getName(map.getId()));
		nameField.setPromptText(MapsInfoPage.displayName(context, saves, map));
		HBox.setHgrow(nameField, Priority.ALWAYS);
		final Button saveButton = Icons.button("Save Name");
		saveButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			context.getMapRegistry().setName(map.getId(), nameField.getText());
			create(context, instance, map);
		});
		final HBox nameForm = new HBox(10, new Label("Name"), nameField, saveButton);

		final SaveInfo save = saves.get(map.getId().toLowerCase());
		final MapCoordinates coordinates = new MapCoordinates(save != null ? save.origin() : MapCoordinates.DEFAULT_ORIGIN);
		final Label originLabel = new Label("Coordinates without = are relative to spawn at x =" + coordinates.getOriginX()
				+ " z =" + coordinates.getOriginZ() + (save == null ? " (assumed: middle of a default size world)" : ""));
		final Label hoverLabel = new Label(" ");
		final Label status = new Label("Rendering map...");
		final MapViewer viewer = new MapViewer(map, coordinates, hoverLabel::setText);
		viewer.setPrefHeight(400);
		VBox.setVgrow(viewer, Priority.ALWAYS);

		final TextField goField = new TextField();
		goField.setPromptText("Go to: x z (from spawn) or =x =z (absolute)");
		HBox.setHgrow(goField, Priority.ALWAYS);
		final Runnable go = () -> {
			try {
				final double[] target = coordinates.parse(goField.getText());
				viewer.goTo(target[0], target[1]);
				status.setText("Showing " + coordinates.describe(target[0], target[1]));
			} catch (IllegalArgumentException e) {
				status.setText(e.getMessage());
			}
		};
		goField.setOnAction(x -> go.run());
		final Button goButton = Icons.button("Go");
		goButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			go.run();
		});
		final Button fitButton = Icons.button("Fit");
		fitButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			viewer.fit();
		});
		final HBox goForm = new HBox(10, new Label("Go to"), goField, goButton, fitButton);
		loadOverview(map, viewer, status);

		context.getRootNode().getChildren().clear();
		context.getRootNode().getChildren().addAll(fileLabel, sourceLabel, nameForm, goForm, originLabel, viewer, hoverLabel, status,
				createBackButton(context, instance));
	}
}
