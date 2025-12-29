package com.yelloowstone.vslauncher;

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;

import java.io.File;

public class FileButtonLabel {

    public static HBox create(final Context context, final String labelText, final File[] initialDirectories, final ObservableList<File> dirs, final ObjectProperty<File> fileProperty) {
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        for(final var initialDirectory: initialDirectories) {
            if(initialDirectory.exists()) {
                directoryChooser.setInitialDirectory(initialDirectory);
                break;
            }
        }

        final Button button = new Button("Add");
        button.setOnAction(x -> {
            final File selectedFile = directoryChooser.showDialog(context.getStage());
            if(selectedFile == null || !selectedFile.isDirectory()) {
                return;
            }
            dirs.add(selectedFile);
        });

        final Label keyLabel = new Label(labelText);
        final ComboBox<File> runtimeDirsComboBox = new ComboBox<>(dirs);
        runtimeDirsComboBox.valueProperty().bindBidirectional(fileProperty);

        final HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getChildren().addAll(keyLabel, runtimeDirsComboBox, button);
        return container;
    }
}
