package com.yelloowstone.vslauncher.gui;

import java.io.File;

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;

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
        	context.getVolumeProperty().play();
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
        
        // 1. Allow the ComboBox to grow and fill available space
        HBox.setHgrow(runtimeDirsComboBox, Priority.ALWAYS);
        runtimeDirsComboBox.setMaxWidth(Double.MAX_VALUE);

        // 2. Set the HBox to take up half the width of its parent
        // Note: This works best if the parent is a VBox or similar layout
        container.prefWidthProperty().bind(context.getStage().widthProperty().divide(2));
        
        container.getChildren().addAll(keyLabel, runtimeDirsComboBox, button);
        return container;
    }
}
