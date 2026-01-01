package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InstanceCopyPage {

    public static HBox createInstanceSelect(final Context context, final String labelText, final ObjectProperty<VintageStoryInstance> property) {
        final ComboBox<VintageStoryInstance> instanceComboBox = new ComboBox<>(context.getInstances());
        instanceComboBox.valueProperty().bindBidirectional(property);

        final Label label = new Label(labelText);

        final HBox container = new HBox(10);
        container.getChildren().addAll(label, instanceComboBox);

        return container;
    }

    public static Button createCopyModsButton(final Context context, final ObjectProperty<VintageStoryInstance> sourceFormProperty, final ObjectProperty<VintageStoryInstance> destinationFormProperty) {

        final Button button = new Button("Copy Mods");
        button.setOnAction(e -> {
        	context.getVolumeProperty().play();
            final VintageStoryInstance source = sourceFormProperty.get();
            final VintageStoryInstance destination = destinationFormProperty.get();
            if(source == null || destination == null) {
            	return;
            }
            source.copy(destination);
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static Button createMergeMapsButton(final Context context, final ObjectProperty<VintageStoryInstance> sourceFormProperty, final ObjectProperty<VintageStoryInstance> destinationFormProperty) {
        final Button button = new Button("Merge Maps");
        button.setOnAction(e -> {
        	context.getVolumeProperty().play();
        });
        
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);
        button.setDisable(true);
        button.setTooltip(new Tooltip("This feature is a work in progress!"));
        
        return button;
    }

    public static void create(final Context context) {
        final ObjectProperty<VintageStoryInstance> sourceFormProperty = new SimpleObjectProperty<>();
        final ObjectProperty<VintageStoryInstance> destinationFormProperty = new SimpleObjectProperty<>();

        if(context.getInstances().size() > 0) {
        	sourceFormProperty.set(context.getInstances().get(0));
        }
        
        if(context.getInstances().size() > 1) {
        	destinationFormProperty.set(context.getInstances().get(1));
        }
        
        final HBox sourceForm = createInstanceSelect(context, "Source", sourceFormProperty);
        final HBox destForm = createInstanceSelect(context, "Destination", destinationFormProperty);

        final Button copyModsButton = createCopyModsButton(context, sourceFormProperty, destinationFormProperty);
        final Button mergeMapsButton = createMergeMapsButton(context, sourceFormProperty, destinationFormProperty);
        final Button backButton = BackButton.create(context);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(sourceForm, destForm, copyModsButton, mergeMapsButton, backButton);
    }
}