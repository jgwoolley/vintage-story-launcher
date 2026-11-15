package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.Nf3tDataMerger;
import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InstanceCopyPage {

    public static HBox createInstanceSelect(final Context context, final String labelText, final ObjectProperty<VintageStoryInstance> property) {
        final ComboBox<VintageStoryInstance> instanceComboBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(context.getClientInstances()));
        instanceComboBox.valueProperty().bindBidirectional(property);

        final Label label = new Label(labelText);

        final HBox container = new HBox(10);
        container.getChildren().addAll(label, instanceComboBox);

        
        // 1. Allow the ComboBox to grow and fill available space
        HBox.setHgrow(instanceComboBox, Priority.ALWAYS);
        instanceComboBox.setMaxWidth(Double.MAX_VALUE);

        // 2. Set the HBox to take up half the width of its parent
        // Note: This works best if the parent is a VBox or similar layout
        container.prefWidthProperty().bind(context.getStage().widthProperty().divide(2));
        
        return container;
    }

    public static Button createCopyModsButton(final Context context, final ObjectProperty<VintageStoryInstance> sourceFormProperty, final ObjectProperty<VintageStoryInstance> destinationFormProperty) {

        final Button button = Icons.button("Copy Mods");
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

    /**
     * Merges the source's ModData/Nf3tData.json (translocators, waypoints, ... of the Nf3t mods) into the
     * destination's. The destination wins on conflicts and is backed up first.
     */
    public static Button createMergeNf3tDataButton(final Context context, final ObjectProperty<VintageStoryInstance> sourceFormProperty, final ObjectProperty<VintageStoryInstance> destinationFormProperty, final Label status) {
        final Button button = Icons.button("Merge Nf3t Data");
        button.setTooltip(new Tooltip("Adds the translocators, waypoints and spawn positions that the Nf3t mods saved in the "
                + "source's ModData/Nf3tData.json to the destination's file. Entries the destination already has are kept, "
                + "and a backup of the destination file is made. Close the game first."));
        button.setOnAction(e -> {
            context.getVolumeProperty().play();
            final VintageStoryInstance source = sourceFormProperty.get();
            final VintageStoryInstance destination = destinationFormProperty.get();
            if(source == null || destination == null) {
                return;
            }
            if(source == destination) {
                status.setText("Pick two different instances.");
                return;
            }
            status.setText("Merging...");
            button.setDisable(true);
            final Task<Nf3tDataMerger.Result> task = new Task<>() {
                @Override
                protected Nf3tDataMerger.Result call() throws Exception {
                    return Nf3tDataMerger.merge(Nf3tDataMerger.fileOf(source), Nf3tDataMerger.fileOf(destination), context.getObjectMapper());
                }
            };
            task.setOnSucceeded(x -> {
                button.setDisable(false);
                status.setText(task.getValue().describe());
            });
            task.setOnFailed(x -> {
                button.setDisable(false);
                status.setText("Could not merge: " + task.getException().getMessage());
            });
            final Thread thread = new Thread(task, "vs-merge-nf3t");
            thread.setDaemon(true);
            thread.start();
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static Button createMergeMapsButton(final Context context, final ObjectProperty<VintageStoryInstance> sourceFormProperty, final ObjectProperty<VintageStoryInstance> destinationFormProperty) {
        final Button button = Icons.button("Merge Maps");
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

        final java.util.List<VintageStoryInstance> clients = context.getClientInstances();
        if(clients.size() > 0) {
        	sourceFormProperty.set(clients.get(0));
        }
        
        if(clients.size() > 1) {
        	destinationFormProperty.set(clients.get(1));
        }
        
        final HBox sourceForm = createInstanceSelect(context, "Source", sourceFormProperty);
        final HBox destForm = createInstanceSelect(context, "Destination", destinationFormProperty);

        final Button copyModsButton = createCopyModsButton(context, sourceFormProperty, destinationFormProperty);
        final Label status = new Label("Merge Nf3t Data adds the source's translocators and waypoints (ModData/Nf3tData.json) to the destination; the destination's own entries win and the file is backed up first. Close the game before merging.");
        status.setWrapText(true);
        final Button mergeNf3tButton = createMergeNf3tDataButton(context, sourceFormProperty, destinationFormProperty, status);
        final Button mergeMapsButton = createMergeMapsButton(context, sourceFormProperty, destinationFormProperty);
        final Button backButton = BackButton.create(context);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(sourceForm, destForm, copyModsButton, mergeNf3tButton, mergeMapsButton, status, backButton);
    }
}