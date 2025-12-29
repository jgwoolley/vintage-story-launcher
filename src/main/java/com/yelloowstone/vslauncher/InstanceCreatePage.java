package com.yelloowstone.vslauncher;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import javafx.scene.layout.HBox;

import java.io.File;

public class InstanceCreatePage {

    public static HBox createFileButtonLabel(final Context context, final String labelText, final File[] initialDirectories, final ObjectProperty<File> fileProperty) {
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

    public static void create(final Context context) {
        final ComboBox<VintageStoryRuntime> runtimesComboBox = new ComboBox<>(context.getRuntimes());
        final HBox instancePathForm = createFileButtonLabel(context,"Instance Path", new File[] {
                new File(System.getProperty("user.home"), ".config"),
        }, context.getInstancePathFormProperty());
        final HBox runtimePathForm = createFileButtonLabel(context, "Runtime Path", new File[] {
                new File("/Applications/Vintage Story.app"),
        }, context.getRuntimePathFormProperty());

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(runtimesComboBox, instancePathForm, runtimePathForm);
    }
}
