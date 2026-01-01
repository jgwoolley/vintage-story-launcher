package com.yelloowstone.vslauncher.gui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InstanceInfoPage {
    public static Button createOpenButton(final Context context, final VintageStoryInstance instance) {
        final Button button = new Button("Open");
        button.setOnAction(x -> {
        	context.getVolumeProperty().play();
            instance.open();
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);
        return button;
    }

    public static Button createOpenFileButton(final Context context, final String textField, final File file) {
        final Button button = new Button(textField);
        button.setOnAction(x -> {
        	context.getVolumeProperty().play();
        	Context.openFile(file);
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);
        return button;
    }

    public static TableColumn<Map.Entry<String,String>, String> createKeyColumn() {
        final TableColumn<Map.Entry<String,String>, String> versionCol = new TableColumn<>("Key");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getKey()));

        return versionCol;
    }

    public static TableColumn<Map.Entry<String,String>, String> createValueColumn() {
        final TableColumn<Map.Entry<String,String>, String> versionCol = new TableColumn<>("Value");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getValue()));

        return versionCol;
    }

    public static TableView<Map.Entry<String, String>> createInstanceTable(final Context context, final VintageStoryInstance instance) {
        final Map<String, String> values = new HashMap<>();
        values.put("name", instance.getName());
        values.put("playername", instance.getPlayer(context.getObjectMapper()));
        values.put("version", instance.getVersion());
        values.put("Runtime Path", instance.getRuntimePath().toString());
        values.put("Data Path", instance.getDataPath().toString());
        values.put("Last Open", instance.getLastOpen().toString());

        final TableView<Map.Entry<String, String>> table = new TableView<>(FXCollections.observableArrayList(values.entrySet()));
        table.getColumns().add(createKeyColumn());
        table.getColumns().add(createValueColumn());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

        public static void create(final Context context, final VintageStoryInstance instance) {
        final Button openButton = createOpenButton(context, instance);
        final Button openRuntimeButton = createOpenFileButton(context, "Open Runtime Path", instance.getRuntimePath());
        final Button openDataButton = createOpenFileButton(context, "Open Data Path", instance.getDataPath());
        final Button backButton = BackButton.create(context);
        final TableView<Map.Entry<String, String>> table = createInstanceTable(context, instance);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(table, openButton, openRuntimeButton, openDataButton, backButton);
    }
}