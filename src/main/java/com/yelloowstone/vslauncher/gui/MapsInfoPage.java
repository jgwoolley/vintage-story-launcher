package com.yelloowstone.vslauncher.gui;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.yelloowstone.vslauncher.VintageStoryInstance;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

public class MapsInfoPage {
	public static Button createBackButton(final Context context, final VintageStoryInstance instance) {
		final Button button = new Button("Back");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			InstanceInfoPage.create(context, instance);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	public static TableColumn<VintageStoryMap, String> createFileColumn() {
		final TableColumn<VintageStoryMap, String> versionCol = new TableColumn<>("Maps");
		versionCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getFile().getName()));

		return versionCol;
	}

	public static TableColumn<VintageStoryMap, Long> createSizeColumn() {
		final TableColumn<VintageStoryMap, Long> versionCol = new TableColumn<>("Size");
		versionCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getFile().length()));

		return versionCol;
	}
	
	public static TableColumn<VintageStoryMap, LocalDateTime> createLastOpenColumn() {
		final TableColumn<VintageStoryMap, LocalDateTime> versionCol = new TableColumn<>("Last Open");
		versionCol.setCellValueFactory(data -> {
	        final Instant instant = Instant.ofEpochMilli(data.getValue().getFile().lastModified());
	        final LocalDateTime value =  LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
			
			return new ReadOnlyObjectWrapper<>(value);
		});

		return versionCol;
	}

	public static TableColumn<VintageStoryMap, Void> createActionColumn(final Context context, final VintageStoryInstance instance) {
        final TableColumn<VintageStoryMap, Void> column = new TableColumn<>("Actions");
        column.setCellFactory(new Callback<TableColumn<VintageStoryMap, Void>, TableCell<VintageStoryMap, Void>>() {
            @Override
            public TableCell<VintageStoryMap, Void> call(TableColumn<VintageStoryMap, Void> vintageStoryInstanceVoidTableColumn) {
                final HBox buttonBar = new HBox();

                final Button openButton = new Button("Merge");
                final Button moreButton = new Button("More");

                // 2. Allow both buttons to grow equally
                HBox.setHgrow(openButton, Priority.ALWAYS);
                HBox.setHgrow(moreButton, Priority.ALWAYS);

                // 3. Force them to actually use the space provided by HGrow
                openButton.setMaxWidth(Double.MAX_VALUE);
                moreButton.setMaxWidth(Double.MAX_VALUE);
                
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
                            final VintageStoryMap map = items.get(getIndex());
                            openButton.setOnAction(x -> {
                            	context.getVolumeProperty().play();
                            	Context.openFile(map.getFile());
                            	MapCopyPage.create(context, instance, map);
                            });
                            moreButton.setOnAction(x -> {
                            	context.getVolumeProperty().play();  
                            	MapInfoPage.create(context, instance, map);
                            });
                            setGraphic(buttonBar);
                        }
                    }
                };
            }
        });

        return column;
    }
	
	public static TableView<VintageStoryMap> createMapsTable(final Context context,
			final VintageStoryInstance instance) {

		final TableView<VintageStoryMap> table = new TableView<>(FXCollections.observableArrayList(instance.getMaps()));
		table.getColumns().add(createFileColumn());
		table.getColumns().add(createSizeColumn());
		table.getColumns().add(createLastOpenColumn());
		table.getColumns().add(createActionColumn(context, instance));
		
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		return table;
	}

	public static void create(final Context context, final VintageStoryInstance instance) {
		context.getRootNode().getChildren().clear();
		context.getRootNode().getChildren().addAll(createMapsTable(context, instance),
				createBackButton(context, instance));
	}
}
