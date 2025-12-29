package com.yelloowstone.vslauncher;

import javafx.beans.property.SimpleStringProperty;

import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InstanceSelectionPage {

    public static TableColumn<VintageStoryInstance, String> createDataPathColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Instance Path");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().toString()));

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, String> createRuntimePathColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Runtime Path");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRuntime().toString()));

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
        final Button button = new Button("Load Last Instance");
        button.setOnAction(e -> {

        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static Button createNewInstanceButton(final Context context) {
        final Button button = new Button("Create New Instance");
        button.setOnAction(e -> {
            InstanceCreatePage.create(context);
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static Button createNewRuntimeButton(final Context context) {
        final Button button = new Button("Create New Runtime");
        button.setOnAction(e -> {
            RuntimeCreatePage.create(context);
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static void create(final Context context) {
        final Button loadLastInstanceButton = createLoadLastInstanceButton(context);
        final TableView<VintageStoryInstance> table = createInstanceTable(context);
        final Button newInstanceButton = createNewInstanceButton(context);
        final Button newRuntimeButton = createNewRuntimeButton(context);

        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(loadLastInstanceButton, table, newInstanceButton, newRuntimeButton);

    }
}
