package com.yelloowstone.vslauncher;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;

public class RuntimeCreatePage {

    public static void create(final Context context) {
        final HBox runtimePathForm = FileButtonLabel.create(context, "Runtime Path", new File[] {
                new File("/Applications/Vintage Story.app"),
        }, context.getRuntimePathFormProperty());

        final Button submitButton = new Button("Submit");
        submitButton.setOnAction(x -> {
            final File path = context.getRuntimePathFormProperty().get();
            if(path == null) {
                return;
            }
            context.getRuntimes().add(new VintageStoryRuntime(path));
            InstanceSelectionPage.create(context);
        });
        submitButton.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(submitButton, Priority.ALWAYS);

        final Button backButton = new Button("Back");
        backButton.setOnAction(x -> {
            InstanceSelectionPage.create(context);
        });
        backButton.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(backButton, Priority.ALWAYS);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(runtimePathForm, submitButton, backButton);
    }
}
