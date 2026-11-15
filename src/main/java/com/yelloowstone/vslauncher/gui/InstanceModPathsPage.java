package com.yelloowstone.vslauncher.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.converter.DefaultStringConverter;

/**
 * Edits the mod folders of an instance. Entries that clientsettings.json defines belong to the game and are
 * read-only; the ones the launcher manages can be edited, added, removed and reordered. Every change is saved at once.
 */
public class InstanceModPathsPage {
	private static final String EXPLANATION = "Vintage Story loads mods from every folder in its mod path list. "
			+ "Entries from the game's clientsettings.json (\"Game settings\") are fixed here because the game owns that file. "
			+ "Folders managed by the launcher are passed to the game at launch with a single --addModPath option, in addition to the game's own list. "
			+ "The first launcher folder is where the Mods page installs and updates mods. "
			+ "Click a launcher path to edit it; without any launcher path the default Mods folder of the data path is used.";

	/** One table row; {@code index} is the position in the launcher's list, or -1 for fixed rows. */
	private record Entry(String path, String source, int index) {
		boolean editable() {
			return index >= 0;
		}
	}

	public static void create(final Context context, final VintageStoryInstance instance) {
		final Label explanation = new Label(EXPLANATION);
		explanation.setWrapText(true);

		final TableView<Entry> table = new TableView<>();
		table.setEditable(true);
		VBox.setVgrow(table, Priority.ALWAYS);

		final TableColumn<Entry, String> pathColumn = new TableColumn<>("Path");
		pathColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path()));
		pathColumn.setSortable(false);
		pathColumn.setCellFactory(column -> new TextFieldTableCell<>(new DefaultStringConverter()) {
			@Override
			public void startEdit() {
				final Entry entry = getTableRow() == null ? null : getTableRow().getItem();
				if (entry != null && entry.editable()) {
					super.startEdit();
				}
			}
		});
		pathColumn.setOnEditCommit(event -> {
			final Entry entry = event.getRowValue();
			final String text = event.getNewValue() == null ? "" : event.getNewValue().trim();
			if (entry.editable() && !text.isEmpty()) {
				final List<File> paths = instance.getModPaths();
				paths.set(entry.index(), new File(text));
				save(context, instance, paths);
			}
			refresh(context, instance, table);
		});

		final TableColumn<Entry, String> sourceColumn = new TableColumn<>("Source");
		sourceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().source()));
		sourceColumn.setSortable(false);
		sourceColumn.setMaxWidth(260);
		sourceColumn.setPrefWidth(200);

		table.getColumns().add(pathColumn);
		table.getColumns().add(sourceColumn);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		refresh(context, instance, table);

		final Button addButton = Icons.button("Add");
		addButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			final DirectoryChooser chooser = new DirectoryChooser();
			if (instance.getDataPath().isDirectory()) {
				chooser.setInitialDirectory(instance.getDataPath());
			}
			final File chosen = chooser.showDialog(context.getStage());
			if (chosen == null || !chosen.isDirectory()) {
				return;
			}
			final List<File> paths = instance.getModPaths();
			if (paths.isEmpty()) {
				// Keep loading the default Mods folder once an explicit list exists.
				paths.add(instance.getDefaultModsPath());
			}
			if (!paths.contains(chosen)) {
				paths.add(chosen);
			}
			save(context, instance, paths);
			refresh(context, instance, table);
		});

		final Button removeButton = Icons.button("Remove");
		removeButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			final Entry entry = table.getSelectionModel().getSelectedItem();
			if (entry == null || !entry.editable()) {
				return;
			}
			final List<File> paths = instance.getModPaths();
			paths.remove(entry.index());
			save(context, instance, paths);
			refresh(context, instance, table);
		});

		final Button topButton = Icons.button("Move to Top");
		topButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			final Entry entry = table.getSelectionModel().getSelectedItem();
			if (entry == null || !entry.editable() || entry.index() == 0) {
				return;
			}
			final List<File> paths = instance.getModPaths();
			paths.add(0, paths.remove(entry.index()));
			save(context, instance, paths);
			refresh(context, instance, table);
		});

		final HBox buttons = new HBox(10, addButton, removeButton, topButton);
		for (final Button button : new Button[] { addButton, removeButton, topButton }) {
			button.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(button, Priority.ALWAYS);
		}

		final Button doneButton = Icons.button("Done");
		doneButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			InstanceInfoPage.create(context, instance);
		});
		doneButton.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(doneButton, Priority.ALWAYS);

		context.getRootNode().getChildren().clear();
		context.getRootNode().getChildren().addAll(explanation, table, buttons, doneButton);
	}

	private static void save(final Context context, final VintageStoryInstance instance, final List<File> paths) {
		try {
			instance.setModPaths(paths);
			context.instanceChanged(instance);
		} catch (RuntimeException e) {
			new Alert(Alert.AlertType.ERROR, "Could not save: " + e.getMessage()).showAndWait();
		}
	}

	private static void refresh(final Context context, final VintageStoryInstance instance, final TableView<Entry> table) {
		final List<Entry> entries = new ArrayList<>();
		for (final String modPath : instance.getConfiguredModPaths(context.getObjectMapper())) {
			final File file = new File(modPath);
			entries.add(new Entry(modPath, "Game settings" + (file.isAbsolute() && !file.isDirectory() ? " (missing)" : ""), -1));
		}
		final List<File> managed = instance.getModPaths();
		if (managed.isEmpty()) {
			final File file = instance.getDefaultModsPath();
			entries.add(new Entry(file.toString(), "Launcher default" + (file.isDirectory() ? "" : " (missing)"), -1));
		}
		for (int i = 0; i < managed.size(); i++) {
			final File file = managed.get(i);
			entries.add(new Entry(file.toString(),
					"Launcher" + (i == 0 ? " (primary)" : "") + (file.isDirectory() ? "" : " (missing)"), i));
		}
		table.setItems(FXCollections.observableArrayList(entries));
	}
}
