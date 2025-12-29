package com.yelloowstone.vslauncher;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

	@Override
	public void start(Stage stage) {
		final VBox root = new VBox(10); // 10px spacing
		final Scene scene = new Scene(root, 400, 300);
		root.setAlignment(Pos.CENTER);
		final var context = new Context(stage, root);
		InstanceSelectionPage.create(context);

		stage.setScene(scene);
		stage.show();
	}

	public static void main(String[] args) {
		launch();
	}
}
