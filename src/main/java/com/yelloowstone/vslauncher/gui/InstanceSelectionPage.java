package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.VintageStoryInstance;
import javafx.beans.property.SimpleStringProperty;

import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;

public class InstanceSelectionPage {

    public static TableColumn<VintageStoryInstance, String> createNameColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Name");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName()));

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, String> createVersionColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Version");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getVersion()));

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, String> createDataPathColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Instance Path");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDataPath().toString()));

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, String> createLastOpenColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Last Open");
        versionCol.setCellValueFactory(data ->
        {
            final LocalDateTime value = data.getValue().getLastOpen();
            return new SimpleStringProperty(value == null ? "" : value.toString());
        });

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, String> createRuntimePathColumn() {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Runtime Path");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRuntimePath().toString()));

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, String> createLastLoginUserColumn(Context context) {
        final TableColumn<VintageStoryInstance, String> versionCol = new TableColumn<>("Player");
        versionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPlayer(context.getObjectMapper())));

        return versionCol;
    }

    public static TableColumn<VintageStoryInstance, Void> createActionColumn(final Context context) {
        final TableColumn<VintageStoryInstance, Void> column = new TableColumn<>("Actions");
        column.setCellFactory(new Callback<TableColumn<VintageStoryInstance, Void>, TableCell<VintageStoryInstance, Void>>() {
            @Override
            public TableCell<VintageStoryInstance, Void> call(TableColumn<VintageStoryInstance, Void> vintageStoryInstanceVoidTableColumn) {
                final HBox buttonBar = new HBox();

                final Button openButton = new Button("Open");
                final Button moreButton = new Button("More");
                buttonBar.getChildren().addAll(openButton, moreButton);

                return new TableCell<>(){
                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);

                        if(empty) {
                            setGraphic(null);
                            openButton.setOnAction(null);
                            moreButton.setOnAction(null);
                        } else {
                            final var items = this.getTableView().getItems();
                            final VintageStoryInstance instance = items.get(getIndex());
                            openButton.setOnAction(x -> {
                                instance.open();
                            });
                            moreButton.setOnAction(x -> {
                                InstanceInfoPage.create(context, instance);
                            });
                            setGraphic(buttonBar);
                        }
                    }
                };
            }
        });

        return column;
    }

    public static TableView<VintageStoryInstance> createInstanceTable(final Context context) {
        final TableView<VintageStoryInstance> table = new TableView<>(context.getInstances());

        table.getColumns().add(createNameColumn());
        table.getColumns().add(createVersionColumn());
        table.getColumns().add(createLastLoginUserColumn(context));
        table.getColumns().add(createLastOpenColumn());
//        table.getColumns().add(createRuntimePathColumn());
//        table.getColumns().add(createDataPathColumn());
        table.getColumns().add(createActionColumn(context));

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    public static Button createLoadLastInstanceButton(final Context context) {
        final SortedList<VintageStoryInstance> sorted = context.getInstances().sorted((a, b) -> a.getLastOpen().compareTo(b.getLastOpen()));
        final VintageStoryInstance instance = sorted == null || sorted.isEmpty() ? null: sorted.get(0);
        final String text = "Load Last Instance (" + (instance == null ? "None": sorted.get(0).getName()) + ")";


        final Button button = new Button(text);
        button.setOnAction(e -> {
            if(context.getInstances().isEmpty()) {
                return;
            }

            sorted.get(0).open();
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

    public static Button createCopyButton(final Context context) {
        final Button button = new Button("Copy Instance Data");
        button.setOnAction(e -> {
            InstanceCopyPage.create(context);
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static Button createOpenConfigButton(final Context context) {
        final Button button = new Button("Open Config");
        button.setOnAction(e -> {
            try {
                Desktop.getDesktop().open(context.getConfigHome());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }

    public static void create(final Context context) {
        final Button loadLastInstanceButton = createLoadLastInstanceButton(context);
        final TableView<VintageStoryInstance> table = createInstanceTable(context);
        final Button newInstanceButton = createNewInstanceButton(context);
        final Button copyButton = createCopyButton(context);
        final Button openConfigButton = createOpenConfigButton(context);


        context.getRootNode().getChildren().clear();
        context.getRootNode().getChildren().addAll(loadLastInstanceButton, table, newInstanceButton, copyButton, openConfigButton);

    }
}
