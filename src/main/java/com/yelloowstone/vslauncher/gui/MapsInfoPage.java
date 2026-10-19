package com.yelloowstone.vslauncher.gui;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import com.yelloowstone.vslauncher.SaveGameScanner;
import com.yelloowstone.vslauncher.SaveGameScanner.SaveInfo;
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
		final Button button = Icons.button("Back");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			InstanceInfoPage.create(context, instance);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	public static TableColumn<VintageStoryMap, String> createFileColumn() {
		final TableColumn<VintageStoryMap, String> versionCol = new TableColumn<>("File");
		versionCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getFile().getName()));

		return versionCol;
	}

	/** What the map is: a save of yours, a server, or unknown. */
	public static String describeSource(final Context context, final Map<String, SaveInfo> saves,
			final VintageStoryMap map) {
		final SaveInfo save = saves.get(map.getId().toLowerCase());
		if (save != null) {
			return "Singleplayer: " + save.displayName();
		}
		final String server = context.getMapRegistry().getServer(map.getId());
		return server != null ? "Server: " + server : "Unknown";
	}

	/** The name you gave the map, else the save's name, else the server, else the file. */
	public static String displayName(final Context context, final Map<String, SaveInfo> saves,
			final VintageStoryMap map) {
		final String name = context.getMapRegistry().getName(map.getId());
		if (name != null) {
			return name;
		}
		final SaveInfo save = saves.get(map.getId().toLowerCase());
		if (save != null) {
			return save.displayName();
		}
		final String server = context.getMapRegistry().getServer(map.getId());
		return server != null ? server : "(unnamed)";
	}

	public static TableColumn<VintageStoryMap, String> createNameColumn(final Context context,
			final Map<String, SaveInfo> saves) {
		final TableColumn<VintageStoryMap, String> column = new TableColumn<>("Name");
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(displayName(context, saves, data.getValue())));
		return column;
	}

	public static TableColumn<VintageStoryMap, String> createSourceColumn(final Context context,
			final Map<String, SaveInfo> saves) {
		final TableColumn<VintageStoryMap, String> column = new TableColumn<>("Source");
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(describeSource(context, saves, data.getValue())));
		return column;
	}

	public static TableColumn<VintageStoryMap, Long> createSizeColumn() {
		final TableColumn<VintageStoryMap, Long> versionCol = new TableColumn<>("Size");
		// The value stays a Long so sorting is numeric; only the rendering is human readable.
		versionCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getFile().length()));
		versionCol.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(final Long size, final boolean empty) {
				super.updateItem(size, empty);
				setText(empty || size == null ? null : formatSize(size));
			}
		});

		return versionCol;
	}

	public static String formatSize(final long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		final String[] units = { "KiB", "MiB", "GiB", "TiB" };
		double value = bytes;
		int unit = -1;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format("%.1f %s", value, units[unit]);
	}
	
	public static TableColumn<VintageStoryMap, LocalDateTime> createLastOpenColumn() {
		final TableColumn<VintageStoryMap, LocalDateTime> versionCol = new TableColumn<>("Last Open");
		versionCol.setCellValueFactory(data -> {
	        final Instant instant = Instant.ofEpochMilli(data.getValue().getFile().lastModified());
	        final LocalDateTime value =  LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
			
			return new ReadOnlyObjectWrapper<>(value);
		});
		versionCol.setCellFactory(HumanTime.cellFactory());

		return versionCol;
	}

	public static TableColumn<VintageStoryMap, Void> createActionColumn(final Context context, final VintageStoryInstance instance) {
        final TableColumn<VintageStoryMap, Void> column = new TableColumn<>("Actions");
        column.setCellFactory(new Callback<TableColumn<VintageStoryMap, Void>, TableCell<VintageStoryMap, Void>>() {
            @Override
            public TableCell<VintageStoryMap, Void> call(TableColumn<VintageStoryMap, Void> vintageStoryInstanceVoidTableColumn) {
                final HBox buttonBar = new HBox();

                final Button openButton = Icons.button("Merge");
                final Button moreButton = Icons.button("More");

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
		final Map<String, SaveInfo> saves = SaveGameScanner.scan(instance.getDataPath());
		table.getColumns().add(createNameColumn(context, saves));
		table.getColumns().add(createSourceColumn(context, saves));
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
