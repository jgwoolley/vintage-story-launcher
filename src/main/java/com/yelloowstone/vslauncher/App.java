package com.yelloowstone.vslauncher;

import com.yelloowstone.vslauncher.gui.Context;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.yelloowstone.vslauncher.gui.InstanceSelectionPage;

public class App extends Application {

	@Override
	public void start(Stage stage) {
		final VBox root = new VBox(10); // 10px spacing
		final Scene scene = new Scene(root, 400, 300);
		scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
		root.setAlignment(Pos.CENTER);
		final var context = new Context(stage, root);
		InstanceSelectionPage.create(context);

		stage.setTitle("VintageStory Launcher");

		stage.setScene(scene);
		stage.show();
	}

	public static void main(String[] args) {
		launch();
	}
}
