package com.yelloowstone.vslauncher.gui;

import java.io.File;

import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InstanceCreatePage {

    public static HBox createTextField(final Context context, final TextField field) {
        final Label label = new Label("Name");

        final HBox container = new HBox(10);
        container.getChildren().addAll(label, field);

        // 1. Allow the ComboBox to grow and fill available space
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);

        // 2. Set the HBox to take up half the width of its parent
        // Note: This works best if the parent is a VBox or similar layout
        container.prefWidthProperty().bind(context.getStage().widthProperty().divide(2));
        
        return container;
    }


    public static void create(final Context context) {
        final TextField nameField = new TextField();
        final ObjectProperty<File> dataPathFormProperty = new SimpleObjectProperty<>();
        final ObjectProperty<File> runtimePathFormProperty = new SimpleObjectProperty<>();

        dataPathFormProperty.addListener(new ChangeListener<File>() {
            @Override
            public void changed(ObservableValue<? extends File> observableValue, File oldValue, File newValue) {
                nameField.setText(newValue.getName());
            }
        });

        if(!context.getDataDirs().isEmpty()) {
            final File file = context.getDataDirs().get(0);
            dataPathFormProperty.set(file);
        }

        if(!context.getRuntimeDirs().isEmpty()) {
            runtimePathFormProperty.set(context.getRuntimeDirs().get(0));
        }

        final HBox instanceNameForm = createTextField(context, nameField);

        final HBox runtimePathForm = FileButtonLabel.create(context,"Runtime Path", new File[] {
                new File("/Applications/Vintage Story.app"),
        }, context.getRuntimeDirs(), runtimePathFormProperty);

        final HBox dataPathForm = FileButtonLabel.create(context,"Instance Path", new File[] {
                new File(System.getProperty("user.home"), "/Documents/VSInstances"),
        }, context.getDataDirs(), dataPathFormProperty);

        final Button submitButton = new Button("Submit");
        submitButton.setOnAction(x -> {
        	context.getVolumeProperty().play();
            final String name = nameField.getText();
            final File runtimePath = runtimePathFormProperty.get();
            final File dataPath = dataPathFormProperty.get();

            if(name == null || name.isEmpty() || runtimePath == null || !runtimePath.isDirectory() || dataPath == null || !dataPath.isDirectory()) {
                return;
            }

            context.getInstances().add(new VintageStoryInstance(name, runtimePath, dataPath));

            InstanceSelectionPage.create(context);
        });
        submitButton.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(submitButton, Priority.ALWAYS);

        final Button backButton = BackButton.create(context);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(instanceNameForm, runtimePathForm, dataPathForm, submitButton, backButton);
    }
}
