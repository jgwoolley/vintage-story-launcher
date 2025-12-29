package com.yelloowstone.vslauncher;

import javafx.beans.property.SimpleStringProperty;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

public class InstanceSelectionPage {

    public static TableColumn<VintageStoryInstance, String> createDataPathColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Instance Path");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDataPath().toString()));

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, String> createRuntimePathColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Runtime Path");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRuntime().getPath().toString()));

        return versionCol;
    }

    public static TableView<VintageStoryInstance> createInstanceTable(final Context context) {
        final TableView<VintageStoryInstance> table = new TableView<>(context.getInstances());

        table.getColumns().add(createDataPathColumn());
        table.getColumns().add(createRuntimePathColumn());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    public static Button createLoadLastInstanceButton(final Context context) {
        final Button viewUploadButton = new Button("Load Last Instance");
        viewUploadButton.setOnAction(e -> {

        });

        return viewUploadButton;
    }

    public static Button createNewInstanceButton(final Context context) {
        final Button viewUploadButton = new Button("Create New Instance");
        viewUploadButton.setOnAction(e -> {
            InstanceCreatePage.create(context);
        });

        return viewUploadButton;
    }

    public static void create(final Context context) {
        final Button loadLastInstanceButton = createLoadLastInstanceButton(context);
        final TableView<VintageStoryInstance> table = createInstanceTable(context);
        final Button newInstanceButton = createNewInstanceButton(context);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(loadLastInstanceButton, table, newInstanceButton);
    }
}
