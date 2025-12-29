package com.yelloowstone.vslauncher;

import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class InstanceInfoPage {
    public static Button createOpenButton(final VintageStoryInstance instance) {
        final Button button = new Button("Open");
        button.setOnAction(x -> {
            instance.open();
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);
        return button;
    }

    public static Button createOpenFileButton(final String textField, final File file) {
        final Button button = new Button(textField);
        button.setOnAction(x -> {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);
        return button;
    }

    public static void create(final Context context, final VintageStoryInstance instance) {
        final Button openButton = createOpenButton(instance);
        final Button openRuntimeButton = createOpenFileButton("Open Runtime Path", instance.getRuntimePath());
        final Button openDataButton = createOpenFileButton("Open Data Path", instance.getDataPath());
        final Button backButton = BackButton.create(context);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(openButton, openRuntimeButton, openDataButton, backButton);
    }
}