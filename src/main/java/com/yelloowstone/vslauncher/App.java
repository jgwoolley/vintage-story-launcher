package com.yelloowstone.vslauncher;

import com.yelloowstone.vslauncher.gui.Context;
import com.yelloowstone.vslauncher.gui.TrayMenu;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.application.Application;
import javafx.event.Event;
import javafx.scene.input.ScrollEvent;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.yelloowstone.vslauncher.gui.InstanceSelectionPage;

public class App extends Application {
	private TrayMenu tray;

	@Override
	public void start(Stage stage) {
		final VBox root = new VBox(10); // 10px spacing
		final Scene scene = new Scene(root, 400, 300);
		scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
		root.setAlignment(Pos.CENTER);
		final var context = new Context(stage, root);
		InstanceSelectionPage.create(context);
		installScrollSpeed(scene, context);

		stage.setTitle("Nf3t’s VintageStory Launcher");

		stage.getIcons().add(new Image(getClass().getResourceAsStream("/icon-64.png")));
		stage.setScene(scene);
		stage.show();

		tray = TrayMenu.install(context, stage);
		if (tray != null) {
			// Hiding the last window must not end the app while it lives in the tray
			Platform.setImplicitExit(false);
			stage.setOnCloseRequest(e -> {
				if (context.getSettings().closeToTray) {
					e.consume();
					stage.hide();
				} else {
					Platform.exit();
				}
			});
		}
	}

	@Override
	public void stop() {
		if (tray != null) {
			tray.remove();
		}
	}

	/** Scales every scroll event by the "scroll speed" setting before the controls see it. */
	private static void installScrollSpeed(final Scene scene, final Context context) {
		final boolean[] rescaling = { false };
		scene.addEventFilter(ScrollEvent.SCROLL, e -> {
			final double factor = context.getSettings().scrollSpeed;
			if (rescaling[0] || factor == 1.0) {
				return;
			}
			e.consume();
			final ScrollEvent scaled = new ScrollEvent(e.getSource(), e.getTarget(), e.getEventType(), e.getX(), e.getY(),
					e.getScreenX(), e.getScreenY(), e.isShiftDown(), e.isControlDown(), e.isAltDown(), e.isMetaDown(),
					e.isDirect(), e.isInertia(), e.getDeltaX() * factor, e.getDeltaY() * factor,
					e.getTotalDeltaX() * factor, e.getTotalDeltaY() * factor, e.getTextDeltaXUnits(),
					e.getTextDeltaX() * factor, e.getTextDeltaYUnits(), e.getTextDeltaY() * factor, e.getTouchCount(),
					e.getPickResult());
			rescaling[0] = true;
			try {
				Event.fireEvent(e.getTarget(), scaled);
			} finally {
				rescaling[0] = false;
			}
		});
	}

	public static void main(String[] args) {
		launch();
	}
}
