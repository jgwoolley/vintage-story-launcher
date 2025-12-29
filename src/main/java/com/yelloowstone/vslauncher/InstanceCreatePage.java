package com.yelloowstone.vslauncher;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;

public class InstanceCreatePage {

    public static void create(final Context context) {
        final HBox runtimeForm = new HBox(10);
        final ComboBox<VintageStoryRuntime> runtimeComboBox = new ComboBox<>(context.getRuntimes());

        runtimeForm.getChildren().addAll(runtimeComboBox);

        final HBox instancePathForm = FileButtonLabel.create(context,"Instance Path", new File[] {
                new File(System.getProperty("user.home"), "/Documents/VSInstances"),
        }, context.getInstancePathFormProperty());

        final Button submitButton = new Button("Submit");
        submitButton.setOnAction(x -> {
            final File path = context.getInstancePathFormProperty().get();
            final var runtime = runtimeComboBox.getValue();
            if(path == null || runtime == null) {
                return;
            }
            context.getInstances().add(new VintageStoryInstance(runtime, path));

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
        context.getRootNode().getChildren().addAll(runtimeForm, instancePathForm, submitButton, backButton);
    }
}
