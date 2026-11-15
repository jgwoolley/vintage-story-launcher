package com.yelloowstone.vslauncher.gui;

import java.io.File;

import com.yelloowstone.vslauncher.ServerConfig;
import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Adds a server instance: a name and a dedicated server's data folder (the one holding serverconfig.json). The rest
 * (server name, port, world) is read from that folder whenever it is shown.
 */
public class ServerCreatePage {

	/** Folders that commonly hold a server's data, offered when they exist and look like one. */
	private static ObservableList<File> candidates() {
		final File home = new File(System.getProperty("user.home"));
		final ObservableList<File> result = FXCollections.observableArrayList();
		for (final File file : new File[] { new File("/var/vintagestory/data"), new File("/srv/vintagestory/data"),
				new File("/srv/vintagestory"), new File(home, ".config/VintagestoryData"),
				new File(home, "AppData/Roaming/VintagestoryData"),
				new File(home, "Library/Application Support/VintagestoryData") }) {
			if (ServerConfig.isServerDataPath(file)) {
				result.add(file);
			}
		}
		return result;
	}

	public static void create(final Context context) {
		final TextField nameField = new TextField();
		nameField.setPromptText("Name shown in the launcher");
		final ObjectProperty<File> dataPathProperty = new SimpleObjectProperty<>();
		final Label info = new Label("Pick the server's data folder: the one with serverconfig.json, Saves/ and Playerdata/. "
				+ "Its name, port and world are read from there.");
		info.setWrapText(true);

		dataPathProperty.addListener((observable, oldValue, newValue) -> {
			if (newValue == null) {
				return;
			}
			if (!ServerConfig.isServerDataPath(newValue)) {
				info.setText("No serverconfig.json in " + newValue + ": that is not a server's data folder.");
				return;
			}
			final ServerConfig config = ServerConfig.read(context.getObjectMapper(), newValue);
			final File save = config.saveFile(newValue);
			info.setText("Server: " + (config.serverName() == null ? "(no name)" : config.serverName())
					+ (config.port() == null ? "" : ", port " + config.port()) + ". World: "
					+ (save == null ? "not found in Saves/" : save.getName()));
			if (nameField.getText().isBlank() && config.serverName() != null) {
				nameField.setText(config.serverName());
			}
		});
		final ObservableList<File> dirs = candidates();
		if (!dirs.isEmpty()) {
			dataPathProperty.set(dirs.get(0));
		}

		final Label nameLabel = new Label("Name");
		HBox.setHgrow(nameField, Priority.ALWAYS);
		final HBox nameForm = new HBox(10, nameLabel, nameField);
		final HBox dataPathForm = FileButtonLabel.create(context, "Server Data Path",
				new File[] { new File("/srv"), new File(System.getProperty("user.home")) }, dirs, dataPathProperty);

		final Button submitButton = Icons.button("Submit");
		submitButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			final File dataPath = dataPathProperty.get();
			if (dataPath == null || !ServerConfig.isServerDataPath(dataPath)) {
				new Alert(Alert.AlertType.WARNING, "Pick a server data folder (one with serverconfig.json).").showAndWait();
				return;
			}
			final String name = nameField.getText().isBlank() ? dataPath.getName() : nameField.getText().trim();
			context.getInstances().add(VintageStoryInstance.server(name, dataPath));
			InstanceSelectionPage.create(context);
		});
		submitButton.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(submitButton, Priority.ALWAYS);

		context.getRootNode().getChildren().clear();
		context.getRootNode().getChildren().addAll(nameForm, dataPathForm, info, submitButton, BackButton.create(context));
	}
}
