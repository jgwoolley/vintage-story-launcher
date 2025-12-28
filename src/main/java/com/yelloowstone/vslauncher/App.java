package com.yelloowstone.vslauncher;

import java.io.File;

import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

	@Override
	public void start(Stage stage) {
		final ObjectProperty<VintageStoryVersion> versionProperty = new SimpleObjectProperty<>(
				new VintageStoryVersion(VintageStoryVersion.OperatingSystem.MACOS, 1, 21, 6, null));
		final ObservableMap<VintageStoryVersion, File> versionsProperty = FXCollections.observableHashMap();

		final VBox root = LibraryPage.create(this, stage, versionProperty, versionsProperty);

		stage.setTitle("Maven + JavaFX (No FXML)");
		
		final Scene scene = new Scene(root, 400, 300);
		stage.setScene(scene);
		stage.show();
	}

	public static void main(String[] args) {
		launch();
	}
}
