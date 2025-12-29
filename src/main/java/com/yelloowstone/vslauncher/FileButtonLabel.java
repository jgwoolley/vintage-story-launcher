package com.yelloowstone.vslauncher;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;

import java.io.File;

public class FileButtonLabel {
    public static HBox create(final Context context, final String labelText, final File[] initialDirectories, final ObjectProperty<File> fileProperty) {
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        for(final var initialDirectory: initialDirectories) {
            if(initialDirectory.exists()) {
                directoryChooser.setInitialDirectory(initialDirectory);
                break;
            }
        }

        final Button button = new Button("Open");
        button.setOnAction(x -> {
            final File selectedFile = directoryChooser.showDialog(context.getStage());
            if(selectedFile != null && selectedFile.exists()) {
                fileProperty.set(selectedFile);
            }
        });

        final Label label = new Label(labelText);
        label.textProperty().bind(fileProperty.map(file ->
                file != null ? file.getName() : "None"
        ));
        final HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getChildren().addAll(label, button);
        return container;
    }
}
