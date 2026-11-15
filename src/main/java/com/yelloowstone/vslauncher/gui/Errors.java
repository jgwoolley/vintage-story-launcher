package com.yelloowstone.vslauncher.gui;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * Keeps one broken instance (a missing folder, an unreadable file) from taking the whole launcher down: values read
 * per instance go through {@link #safe}, and anything that still escapes on the JavaFX thread is shown once as a
 * message instead of ending the app (an exception in {@code Application.start} exits it).
 */
public final class Errors {
	private static final Set<String> shown = new HashSet<>();
	private static boolean recovering;

	private Errors() {
	}

	/** {@code supplier}'s value, or {@code fallback} when it throws (logged once per message). */
	public static <T> T safe(final Supplier<T> supplier, final T fallback) {
		try {
			return supplier.get();
		} catch (RuntimeException e) {
			log(e);
			return fallback;
		}
	}

	private static boolean log(final Throwable e) {
		final String key = e.getClass().getName() + ": " + e.getMessage();
		synchronized (shown) {
			if (!shown.add(key)) {
				return false;
			}
		}
		System.err.println("Error: " + key);
		e.printStackTrace();
		return true;
	}

	/**
	 * Installs the safety net for the JavaFX thread: an uncaught exception is logged, shown in a dialog (each distinct
	 * error once, so a failing table cell does not open one per row) and, if it left the window empty, the instance
	 * list is shown again so there is always a way back.
	 */
	public static void install(final Context context) {
		final Thread.UncaughtExceptionHandler previous = Thread.currentThread().getUncaughtExceptionHandler();
		Thread.currentThread().setUncaughtExceptionHandler((thread, e) -> {
			if (!Platform.isFxApplicationThread()) {
				previous.uncaughtException(thread, e);
				return;
			}
			if (!log(e)) {
				return;
			}
			if (context.getRootNode().getChildren().isEmpty() && !recovering) {
				recovering = true;
				try {
					InstanceSelectionPage.create(context);
				} catch (RuntimeException again) {
					log(again);
				} finally {
					recovering = false;
				}
			}
			// Not inside layout or a cell update: show the dialog afterwards
			Platform.runLater(() -> {
				final Alert alert = new Alert(Alert.AlertType.ERROR,
						"Something went wrong, but the launcher keeps running:\n\n" + e
								+ "\n\nDetails are in the terminal output.");
				alert.setHeaderText("Error");
				alert.show();
			});
		});
	}
}
