package com.yelloowstone.vslauncher.gui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.yelloowstone.vslauncher.SaveGameScanner;
import com.yelloowstone.vslauncher.ServerConfig;
import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DefaultStringConverter;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InstanceInfoPage {
	public static Button createOpenButton(final Context context, final VintageStoryInstance instance) {
		final Button button = Icons.button("Open");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			instance.open(context.getObjectMapper(), context.getMapRegistry());
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	public static Button createOpenFileButton(final Context context, final String textField, final File file) {
		final Button button = Icons.button(textField);
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			Context.openFile(file);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	public static Button createOpenMapsButton(final Context context, final VintageStoryInstance instance) {
		final Button button = Icons.button("Open Maps");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			if (instance.isServer()) {
				ServerMapPage.create(context, instance);
			} else {
				MapsInfoPage.create(context, instance);
			}
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	public static Button createManageModsButton(final Context context, final VintageStoryInstance instance) {
		final Button button = Icons.button("Manage Mods");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			ModsPage.create(context, instance);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	/** Hover text for the Key column. */
	private static final Map<String, String> HELP = Map.ofEntries(
			Map.entry("Name", "How this instance is called in the launcher. Changing it does not touch any game files."),
			Map.entry("Runtime Path", "The folder of the installed game (the one containing Vintagestory / run.sh). "
					+ "Point it at another folder to play this instance with a different game version."),
			Map.entry("Data Path", "The game's data folder: saves, maps, logs and clientsettings.json. "
					+ "Changing it only points the instance elsewhere; no files are moved."),
			Map.entry("Launch Arguments", "Extra command line options added when the game starts, for example "
					+ "--tracelog. Put quotes around values that contain spaces. Leave empty for none."),
			Map.entry("Wrapper Command", "A program that starts the game for you, put in front of it, for example "
					+ "gamemoderun or prime-run. Leave empty for none. Not used on macOS."),
			Map.entry("Environment", "Environment variables (settings handed to the game's process) written as "
					+ "KEY=value, separated by spaces, for example MESA_GL_VERSION_OVERRIDE=4.5. Mostly used to work "
					+ "around graphics or driver problems. Leave empty for none. Not used on macOS."),
			Map.entry("Mod Paths", "The folders the game loads mods from: the ones in the game's clientsettings.json "
					+ "plus the launcher's own (added at launch). Use Edit to change the launcher's."),
			Map.entry("Player Name", "The player name saved in the game's clientsettings.json (calculated)."),
			Map.entry("Version", "The game version of the runtime path (calculated)."),
			Map.entry("Server Data Path", "The dedicated server's data folder: serverconfig.json, Saves/ and Playerdata/. "
					+ "Changing it only points the instance elsewhere; no files are moved."),
			Map.entry("Server Name", "ServerName from the server's serverconfig.json (calculated)."),
			Map.entry("Port", "Port from the server's serverconfig.json (calculated)."),
			Map.entry("World", "The save the server runs: WorldConfig.SaveFileLocation in serverconfig.json, looked up in "
					+ "this folder's Saves/ when that path is from another machine or container (calculated)."),
			Map.entry("World ID", "The world's savegame identifier. Clients name their map of this world <ID>.db, which is "
					+ "how Open Maps finds them (calculated)."),
			Map.entry("Client Maps", "Client instances that have a map of this world (calculated)."),
			Map.entry("Last Saved", "When the server last wrote one of its saves (calculated)."),
			Map.entry("Last Open", "When the game last saved clientsettings.json, which is when it was last played (calculated)."));

	/** A table row; rows without a setter are calculated and cannot be edited. */
	private record Row(String key, String value, Consumer<String> setter, List<Action> actions) {
		Row(final String key, final String value, final Consumer<String> setter) {
			this(key, value, setter, List.of());
		}
	}

	/** A button shown in the Actions column of the row it works on. */
	private record Action(String label, String icon, Runnable run) {
		Action(final String label, final Runnable run) {
			this(label, label, run);
		}
	}

	private static void warn(final String message) {
		new Alert(Alert.AlertType.WARNING, message).showAndWait();
	}

	/** Applies an edited directory value, refusing it when it is not an existing folder. */
	private static Consumer<String> directorySetter(final String label, final Consumer<File> apply) {
		return text -> {
			final File file = new File(text.trim());
			if (!file.isDirectory()) {
				warn(label + " must be an existing folder: " + text);
				return;
			}
			apply.accept(file);
		};
	}

	private static String blankToNull(final String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

	/** The rows of a server instance: only the name and folder are its own, the rest is read from the folder. */
	private static List<Row> createServerRows(final Context context, final VintageStoryInstance instance) {
		final File dataPath = instance.getDataPath();
		final ServerConfig config = ServerConfig.read(context.getObjectMapper(), dataPath);
		final File saveFile = config.saveFile(dataPath);
		final SaveGameScanner.SaveInfo save = saveFile == null ? null : SaveGameScanner.read(saveFile);
		final List<String> clients = save == null ? List.of() : ServerMapPage.clientMaps(context, save.id()).stream()
				.map(ServerMapPage.ClientMap::toString).toList();
		final List<Row> rows = new ArrayList<>();
		rows.add(new Row("Server Data Path", dataPath.toString(), text -> {
			final File file = new File(text.trim());
			if (!ServerConfig.isServerDataPath(file)) {
				warn("The server data path must be a folder with serverconfig.json: " + text);
				return;
			}
			instance.setDataPath(file);
		}, List.of(new Action("Open", "Open Folder", () -> Context.openFile(dataPath)))));
		rows.add(new Row("Server Name", config.serverName() == null ? "" : config.serverName(), null));
		rows.add(new Row("Port", config.port() == null ? "" : config.port().toString(), null));
		rows.add(new Row("World", saveFile == null
				? (config.saveFileLocation() == null ? "not found" : config.saveFileLocation() + " (not found)")
				: saveFile + (save == null ? " (unreadable)" : ""), null));
		rows.add(new Row("World ID", save == null ? "" : save.id(), null));
		rows.add(new Row("Client Maps", clients.isEmpty() ? "none: no client instance has played this world" : String.join("; ", clients), null));
		rows.add(new Row("Last Saved", HumanTime.describe(instance.getLastOpen()), null));
		return rows;
	}

	private static TableView<Row> createInstanceTable(final Context context, final VintageStoryInstance instance) {
		final List<Row> rows = new ArrayList<>();
		rows.add(new Row("Name", instance.getName(), text -> {
			if (text.isBlank()) {
				warn("The name cannot be empty.");
			} else {
				instance.setName(text.trim());
			}
		}));
		if (instance.isServer()) {
			rows.addAll(createServerRows(context, instance));
		} else {
			rows.add(new Row("Runtime Path", instance.getRuntimePath().toString(),
					directorySetter("The runtime path", instance::setRuntimePath),
					List.of(new Action("Open", "Open Folder", () -> Context.openFile(instance.getRuntimePath())))));
			rows.add(new Row("Data Path", instance.getDataPath().toString(),
					directorySetter("The data path", instance::setDataPath),
					List.of(new Action("Open", "Open Folder", () -> Context.openFile(instance.getDataPath())))));
			rows.add(new Row("Launch Arguments", instance.getLaunchArgs() == null ? "" : instance.getLaunchArgs(),
					text -> instance.setLaunchArgs(blankToNull(text))));
			rows.add(new Row("Wrapper Command", instance.getWrapper() == null ? "" : instance.getWrapper(),
					text -> instance.setWrapper(blankToNull(text))));
			rows.add(new Row("Environment", instance.getEnvironment() == null ? "" : instance.getEnvironment(),
					text -> instance.setEnvironment(blankToNull(text))));
			rows.add(new Row("Mod Paths", describeModPaths(context, instance), null, List.of(
					new Action("Edit", () -> InstanceModPathsPage.create(context, instance)))));
			rows.add(new Row("Player Name", instance.getPlayer(context.getObjectMapper()), null));
			rows.add(new Row("Version", instance.getVersion(), null));
			rows.add(new Row("Last Open", HumanTime.describe(instance.getLastOpen()), null));
		}

		final TableColumn<Row, String> keyColumn = new TableColumn<>("Key");
		keyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().key()));
		keyColumn.setSortable(false);
		keyColumn.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(final String key, final boolean empty) {
				super.updateItem(key, empty);
				setText(empty ? null : key);
				setTooltip(empty || HELP.get(key) == null ? null : new Tooltip(HELP.get(key)));
				if (getTooltip() != null) {
					getTooltip().setWrapText(true);
					getTooltip().setMaxWidth(400);
				}
			}
		});

		final TableColumn<Row, String> valueColumn = new TableColumn<>("Value (click to edit, calculated rows are fixed)");
		valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().value()));
		valueColumn.setSortable(false);
		valueColumn.setCellFactory(column -> new TextFieldTableCell<>(new DefaultStringConverter()) {
			@Override
			public void startEdit() {
				final Row row = getTableRow() == null ? null : getTableRow().getItem();
				if (row != null && row.setter() != null) {
					super.startEdit();
				}
			}
		});
		valueColumn.setOnEditCommit(event -> {
			final Row row = event.getRowValue();
			if (row.setter() != null && event.getNewValue() != null && !event.getNewValue().equals(row.value())) {
				row.setter().accept(event.getNewValue());
				context.instanceChanged(instance);
			}
			// Re-render so the table shows what was actually stored (invalid edits revert).
			create(context, instance);
		});

		final TableColumn<Row, Void> actionColumn = new TableColumn<>("Actions");
		actionColumn.setSortable(false);
		actionColumn.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(final Void item, final boolean empty) {
				super.updateItem(item, empty);
				final Row row = empty || getTableRow() == null ? null : getTableRow().getItem();
				if (row == null || row.actions().isEmpty()) {
					setGraphic(null);
					return;
				}
				final HBox bar = new HBox();
				for (final Action action : row.actions()) {
					final Button button = Icons.button(action.label(), action.icon());
					button.setMaxWidth(Double.MAX_VALUE);
					HBox.setHgrow(button, Priority.ALWAYS);
					button.setOnAction(x -> {
						context.getVolumeProperty().play();
						action.run().run();
					});
					bar.getChildren().add(button);
				}
				setGraphic(bar);
			}
		});

		final TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows));
		table.setEditable(true);
		table.getColumns().add(keyColumn);
		table.getColumns().add(valueColumn);
		table.getColumns().add(actionColumn);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		return table;
	}

	private static String describeModPaths(final Context context, final VintageStoryInstance instance) {
		final List<String> parts = new ArrayList<>();
		for (final String modPath : instance.getConfiguredModPaths(context.getObjectMapper())) {
			final File file = new File(modPath);
			parts.add(modPath + (file.isAbsolute() && !file.isDirectory() ? " (missing)" : ""));
		}
		for (final File file : instance.getManagedModPaths()) {
			parts.add(file + (file.isDirectory() ? "" : " (missing)") + " (added at launch)");
		}
		return String.join("; ", parts);
	}

	public static void create(final Context context, final VintageStoryInstance instance) {
		final Button openButton = createOpenButton(context, instance);
		final Button backButton = BackButton.create(context);
		final Button openMapsButton = createOpenMapsButton(context, instance);
		final Button manageModsButton = createManageModsButton(context, instance);
		final TableView<Row> table = createInstanceTable(context, instance);

		context.getRootNode().getChildren().clear();
		if (instance.isServer()) {
			// Nothing to launch or mods to manage: a server instance is for looking at its world
			context.getRootNode().getChildren().addAll(table, openMapsButton, backButton);
			return;
		}
		context.getRootNode().getChildren().addAll(table, openButton, manageModsButton, openMapsButton, backButton);
	}
}
