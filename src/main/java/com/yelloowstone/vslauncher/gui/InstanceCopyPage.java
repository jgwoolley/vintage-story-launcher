package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.VintageStoryInstance;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;

public class InstanceCopyPage {

    public static HBox createInstanceSelect(final Context context, final String labelText, final ObjectProperty<VintageStoryInstance> property) {
        final ComboBox<VintageStoryInstance> instanceComboBox = new ComboBox<>(context.getInstances());
        instanceComboBox.valueProperty().bindBidirectional(property);

        final Label label = new Label(labelText);

        final HBox container = new HBox(10);
        container.getChildren().addAll(label, instanceComboBox);

        return container;
    }

    public static Button createCopyModsButton(final ObjectProperty<VintageStoryInstance> sourceFormProperty, final ObjectProperty<VintageStoryInstance> destinationFormProperty) {

        final Button button = new Button("Copy Mods");
        button.setOnAction(e -> {
            final VintageStoryInstance source = sourceFormProperty.get();
            final VintageStoryInstance destination = destinationFormProperty.get();
            source.copy(destination);
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static Button createMergeMapsButton(final ObjectProperty<VintageStoryInstance> sourceFormProperty, final ObjectProperty<VintageStoryInstance> destinationFormProperty) {
        final Button button = new Button("Merge Maps");
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static void create(final Context context) {
        final ObjectProperty<VintageStoryInstance> sourceFormProperty = new SimpleObjectProperty<>();
        final ObjectProperty<VintageStoryInstance> destinationFormProperty = new SimpleObjectProperty<>();

        final HBox sourceForm = createInstanceSelect(context, "Source", sourceFormProperty);
        final HBox destForm = createInstanceSelect(context, "Destination", destinationFormProperty);

        final Button backButton = BackButton.create(context);
        final Button copyModsButton = createCopyModsButton(sourceFormProperty, destinationFormProperty);
        final Button mergeMapsButton = createMergeMapsButton(sourceFormProperty, destinationFormProperty);


        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(sourceForm, destForm, copyModsButton, mergeMapsButton, backButton);
    }
}