package com.yelloowstone.vslauncher;

import java.io.File;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.yelloowstone.vslauncher.VintageStoryVersion.OperatingSystem;

import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class UploadPage {
	private static ComboBox<VintageStoryVersion.OperatingSystem> createOperatingSystemComboBox(
			final ObjectProperty<VintageStoryVersion> versionProperty) {
		final ComboBox<VintageStoryVersion.OperatingSystem> operatingSystemComboBox = new ComboBox<>(
				FXCollections.observableArrayList(VintageStoryVersion.OperatingSystem.values()));
		operatingSystemComboBox.setValue(VintageStoryVersion.OperatingSystem.MACOS);
		operatingSystemComboBox.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
			if (!isNowFocused) { // Focus lost
				try {
					OperatingSystem newVal = operatingSystemComboBox.getValue();
					// Replace the whole object to trigger the listener
					VintageStoryVersion current = versionProperty.get();
					versionProperty.set(current.withOperatingSystem(newVal));
				} catch (NumberFormatException e) {
					// Reset text to old value on error
					operatingSystemComboBox.setValue(versionProperty.get().getOperatingSystem());
				}
			}
		});

		return operatingSystemComboBox;
	}

	private static TextField createNumberTextField(final ObjectProperty<VintageStoryVersion> versionProperty,
			Function<VintageStoryVersion, Integer> getValue,
			BiFunction<VintageStoryVersion, Integer, VintageStoryVersion> createNew) {
		final TextField numberField = new TextField(Integer.toString(getValue.apply(versionProperty.get())));

		final UnaryOperator<TextFormatter.Change> filter = change -> {
			String text = change.getControlNewText();
			if (text.matches("\\d*")) {
				return change;
			}
			return null;
		};

		numberField.setTextFormatter(new TextFormatter<>(filter));

		numberField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
			if (!isNowFocused) { // Focus lost
				try {
					final int newVal = Integer.parseInt(numberField.getText());
					// Replace the whole object to trigger the listener
					final VintageStoryVersion current = versionProperty.get();

					versionProperty.set(createNew.apply(current, newVal));
				} catch (NumberFormatException e) {
					final int oldVal = getValue.apply(versionProperty.get());
					// Reset text to old value on error
					numberField.setText(String.valueOf(oldVal));
				}
			}
		});

		return numberField;
	}

	private static Button createUploadButton(final Stage stage,
			final ObjectProperty<VintageStoryVersion> versionProperty,
			final ObservableMap<VintageStoryVersion, File> versions) {
		final Button uploadButton = new Button("Upload client");
		final FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Vintage Story Installation/Archive");

		// Optional: Add filters for specific file types
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Archive Files", "*.zip", "*.tar.gz"),
				new FileChooser.ExtensionFilter("All Files", "*.*"));

		uploadButton.setOnAction(e -> {
			File selectedFile = fileChooser.showOpenDialog(stage);
			if (selectedFile != null) {
				versions.put(versionProperty.get(), selectedFile);
				versions.entrySet().forEach(x -> {
					System.out.print(x.getKey());
					System.out.print(' ');
					System.out.println(x.getValue());
				});
			}
		});
		return uploadButton;
	}

	private static Label createLabel(final ObjectProperty<VintageStoryVersion> version) {
		final Label label = new Label(version.get().getVersionString());
		version.addListener((obs, oldVal, newVal) -> {
			if (newVal != null) {
				label.setText(newVal.getVersionString());
			}
		});

		return label;
	}

	private static TextField createIdentifierTextField(final ObjectProperty<VintageStoryVersion> versionProperty) {
		final TextField identifierTextField = new TextField(versionProperty.get().getIdentifier());

		identifierTextField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
			if (!isNowFocused) { // Focus lost
				try {
					String newVal = identifierTextField.getText();
					// Replace the whole object to trigger the listener
					VintageStoryVersion current = versionProperty.get();
					versionProperty.set(current.withIdentifier(newVal));
				} catch (NumberFormatException e) {
					// Reset text to old value on error
					identifierTextField.setText(String.valueOf(versionProperty.get().getPatchVersion()));
				}
			}
		});

		return identifierTextField;
	}

	private static Hyperlink createDownloadClientHyperLink(final Application application, final ObjectProperty<VintageStoryVersion> versionProperty) {
		final Hyperlink link = new Hyperlink("Download Client");
		link.setOnAction(e -> {
			final String url = versionProperty.get().getClientUrl();
			// This opens the URL in the system's default web browser
			application.getHostServices().showDocument(url);
		});
		
		return link;
	}
	
	public static VBox create(final Application application, final Stage stage, final ObjectProperty<VintageStoryVersion> versionProperty, final ObservableMap<VintageStoryVersion, File> versionsProperty) {
		final Label label = createLabel(versionProperty);
		final ComboBox<VintageStoryVersion.OperatingSystem> operatingSystemComboBox = createOperatingSystemComboBox(
				versionProperty);
		final TextField majorVersionTextField = createNumberTextField(versionProperty, x -> x.getMajorVersion(),
				(x, newVal) -> x.withMajorVersion(newVal));
		final TextField minorVersionTextField = createNumberTextField(versionProperty, x -> x.getMinorVersion(),
				(x, newVal) -> x.withMinorVersion(newVal));
		final TextField patchVersionTextField = createNumberTextField(versionProperty, x -> x.getPatchVersion(),
				(x, newVal) -> x.withPatchVersion(newVal));
		final TextField identifierTextField = createIdentifierTextField(versionProperty);

		final Hyperlink link = createDownloadClientHyperLink(application, versionProperty);

		final Button uploadButton = createUploadButton(stage, versionProperty, versionsProperty);

		final Button viewLibraryButton = new Button("View Library");
	    viewLibraryButton.setOnAction(e -> {
	        VBox libraryPage = LibraryPage.create(application, stage, versionProperty, versionsProperty);
			final Scene scene = new Scene(libraryPage, 400, 300);
	        scene.setRoot(libraryPage);
			stage.setScene(scene);
	    });
		
		// Layout: Use a VBox to stack elements vertically
		final VBox root = new VBox(10); // 10px spacing
		root.setAlignment(Pos.CENTER);
		root.getChildren().addAll(label, operatingSystemComboBox, majorVersionTextField, minorVersionTextField,
				patchVersionTextField, identifierTextField, link, uploadButton, viewLibraryButton);
		
		
		return root;
	}
}
