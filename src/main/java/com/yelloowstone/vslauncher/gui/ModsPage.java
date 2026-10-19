package com.yelloowstone.vslauncher.gui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import com.yelloowstone.vslauncher.VintageStoryInstance;
import com.yelloowstone.vslauncher.mods.LocalMod;
import com.yelloowstone.vslauncher.mods.ModManifest;
import com.yelloowstone.vslauncher.mods.ModUpdater;
import com.yelloowstone.vslauncher.mods.ModUpdater.ModStatus;
import com.yelloowstone.vslauncher.mods.ModUpdater.PlanEntry;
import com.yelloowstone.vslauncher.mods.ModUpdater.Plan;
import com.yelloowstone.vslauncher.mods.ModUpdater.Request;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Lists an instance's mods with icon, description, installed/available versions and update buttons. */
public class ModsPage {
	private static final int ICON_SIZE = 48;

	/** One table row; the available version and status fill in as update checks come back. */
	private static class ModRow {
		final LocalMod mod;
		/** Where the mod was found: Instance, Server or Mod path. */
		final String origin;
		final StringProperty available = new SimpleStringProperty("checking...");
		final StringProperty status = new SimpleStringProperty("");
		final BooleanProperty updatable = new SimpleBooleanProperty(false);

		ModRow(final LocalMod mod, final String origin) {
			this.mod = mod;
			this.origin = origin;
		}

		void apply(final ModStatus result, final boolean readOnly) {
			if (result.error() != null) {
				available.set("n/a");
				status.set(result.error());
			} else {
				available.set(result.latest().version());
				status.set(!result.isUpdateAvailable() ? "Up to date"
						: readOnly ? "Newer version exists" : "Update available");
			}
			// Mods in ModsByServer are managed by the game, so they are shown but not updated here.
			updatable.set(result.isUpdateAvailable() && !readOnly);
		}
	}

	/** A mod and the kind of folder it was found in. */
	private record Scanned(String origin, LocalMod mod) {
	}

	/** A folder of mods: the instance's own, or one that a server made the game download. */
	private record Source(String label, File dir, boolean readOnly) {
		@Override
		public String toString() {
			return label;
		}
	}

	private final Context context;
	private final VintageStoryInstance instance;
	private final ModUpdater updater = new ModUpdater();
	private final ObservableList<ModRow> rows = FXCollections.observableArrayList();
	private final Label summary = new Label();
	private final Button refreshButton = Icons.button("Check for Updates");
	private final Button updateAllButton = Icons.button("Update All");
	private List<LocalMod> localMods = List.of();
	private List<Request> missingDependencies = List.of();
	private final ComboBox<Source> sourceBox = new ComboBox<>();
	private boolean busy;

	private ModsPage(final Context context, final VintageStoryInstance instance) {
		this.context = context;
		this.instance = instance;
	}

	public static void create(final Context context, final VintageStoryInstance instance) {
		final ModsPage page = new ModsPage(context, instance);
		page.show();
		page.refresh();
	}

	private void show() {
		final TableView<ModRow> table = new TableView<>(rows);
		table.setFixedCellSize(ICON_SIZE + 12);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		table.getColumns().add(createIconColumn());
		table.getColumns().add(createNameColumn());
		table.getColumns().add(createTextColumn("Location", row -> new SimpleStringProperty(row.origin)));
		table.getColumns().add(createTextColumn("Installed", row -> new SimpleStringProperty(row.mod.getVersion())));
		table.getColumns().add(createTextColumn("Available", row -> row.available));
		table.getColumns().add(createTextColumn("Status", row -> row.status));
		table.getColumns().add(createActionColumn());
		VBox.setVgrow(table, Priority.ALWAYS);

		refreshButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			refresh();
		});
		updateAllButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			final List<Request> requests = new ArrayList<>();
			for (final ModRow row : rows) {
				if (row.updatable.get()) {
					requests.add(new Request(row.mod.getModid(), null));
				}
			}
			requests.addAll(missingDependencies);
			install(requests);
		});
		updateAllButton.setDisable(true);

		final Button exportButton = Icons.button("Export Manifest");
		exportButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			exportManifest();
		});

		final Button openFolderButton = InstanceInfoPage.createOpenFileButton(context, "Open Mod Path",
				instance.getModsPath());
		final Button backButton = Icons.button("Back");
		backButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			InstanceInfoPage.create(context, instance);
		});

		sourceBox.setItems(findSources());
		sourceBox.getSelectionModel().selectFirst();
		sourceBox.setMaxWidth(Double.MAX_VALUE);
		sourceBox.setOnAction(x -> refresh());
		HBox.setHgrow(sourceBox, Priority.ALWAYS);
		final HBox sourceBar = new HBox(10, new Label("Mods"), sourceBox);

		final HBox buttons = new HBox(10, refreshButton, updateAllButton, exportButton, openFolderButton, backButton);
		for (final Button button : List.of(refreshButton, updateAllButton, exportButton, openFolderButton, backButton)) {
			HBox.setHgrow(button, Priority.ALWAYS);
			button.setMaxWidth(Double.MAX_VALUE);
		}

		context.getRootNode().getChildren().clear();
		context.getRootNode().getChildren().addAll(sourceBar, summary, table, buttons);
	}

	/** The instance's own mods first, then each ModsByServer/<host-port> folder. */
	private ObservableList<Source> findSources() {
		final ObservableList<Source> sources = FXCollections.observableArrayList();
		sources.add(new Source("Instance mods", instance.getModsPath(), false));
		final File[] servers = new File(instance.getDataPath(), "ModsByServer").listFiles(File::isDirectory);
		if (servers != null) {
			java.util.Arrays.sort(servers, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
			for (final File server : servers) {
				sources.add(new Source("Server: " + server.getName(), server, true));
			}
		}
		return sources;
	}

	private Source source() {
		final Source selected = sourceBox.getValue();
		return selected != null ? selected : new Source("Instance mods", instance.getModsPath(), false);
	}

	// ------------------------------------------------------------------ columns

	private TableColumn<ModRow, ModRow> createIconColumn() {
		final TableColumn<ModRow, ModRow> column = new TableColumn<>("");
		column.setMinWidth(ICON_SIZE + 16);
		column.setMaxWidth(ICON_SIZE + 16);
		column.setSortable(false);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		column.setCellFactory(c -> new TableCell<>() {
			private final ImageView view = new ImageView();

			{
				view.setFitWidth(ICON_SIZE);
				view.setFitHeight(ICON_SIZE);
				view.setPreserveRatio(true);
			}

			@Override
			protected void updateItem(final ModRow row, final boolean empty) {
				super.updateItem(row, empty);
				if (empty || row == null || row.mod.getIcon() == null) {
					setGraphic(null);
					return;
				}
				final Image image = new Image(new ByteArrayInputStream(row.mod.getIcon()), ICON_SIZE * 2,
						ICON_SIZE * 2, true, true);
				view.setImage(image.isError() ? null : image);
				setGraphic(view);
			}
		});
		return column;
	}

	private TableColumn<ModRow, ModRow> createNameColumn() {
		final TableColumn<ModRow, ModRow> column = new TableColumn<>("Mod");
		column.setMinWidth(220);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		column.setCellFactory(c -> new TableCell<>() {
			private final Label name = new Label();
			private final Label description = new Label();
			private final VBox box = new VBox(2, name, description);

			{
				name.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-text-background-color;");
				description.setStyle("-fx-text-fill: -fx-text-background-color;");
				description.setWrapText(true);
				description.setMaxHeight(ICON_SIZE - 16);
				box.setMaxWidth(Double.MAX_VALUE);
				// Wrap the description to the column rather than widening it
				box.maxWidthProperty().bind(column.widthProperty().subtract(20));
				description.maxWidthProperty().bind(box.maxWidthProperty());
			}

			@Override
			protected void updateItem(final ModRow row, final boolean empty) {
				super.updateItem(row, empty);
				if (empty || row == null) {
					setGraphic(null);
					setTooltip(null);
					return;
				}
				name.setText(row.mod.getName());
				final String text = row.mod.getDescription() == null ? "" : row.mod.getDescription().strip();
				description.setText(text);
				setTooltip(text.isEmpty() ? null : new Tooltip(text));
				setGraphic(box);
			}
		});
		return column;
	}

	private TableColumn<ModRow, String> createTextColumn(final String title,
			final java.util.function.Function<ModRow, javafx.beans.value.ObservableValue<String>> getter) {
		final TableColumn<ModRow, String> column = new TableColumn<>(title);
		column.setCellValueFactory(data -> getter.apply(data.getValue()));
		return column;
	}

	private TableColumn<ModRow, ModRow> createActionColumn() {
		final TableColumn<ModRow, ModRow> column = new TableColumn<>("");
		column.setSortable(false);
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
		column.setCellFactory(c -> new TableCell<>() {
			private final Button button = Icons.button("Update");

			{
				button.setMaxWidth(Double.MAX_VALUE);
			}

			@Override
			protected void updateItem(final ModRow row, final boolean empty) {
				super.updateItem(row, empty);
				if (empty || row == null) {
					setGraphic(null);
					button.disableProperty().unbind();
					return;
				}
				button.disableProperty().bind(row.updatable.not());
				button.setOnAction(x -> {
					context.getVolumeProperty().play();
					install(List.of(new Request(row.mod.getModid(), null)));
				});
				setGraphic(button);
			}
		});
		return column;
	}

	// ------------------------------------------------------------------- actions

	private <T> void async(final Callable<T> work, final Consumer<T> onDone) {
		final Task<T> task = new Task<>() {
			@Override
			protected T call() throws Exception {
				return work.call();
			}
		};
		task.setOnSucceeded(x -> onDone.accept(task.getValue()));
		task.setOnFailed(x -> {
			setBusy(false);
			summary.setText("Failed: " + task.getException());
		});
		final Thread thread = new Thread(task, "vs-mods");
		thread.setDaemon(true);
		thread.start();
	}

	private void setBusy(final boolean value) {
		busy = value;
		refreshButton.setDisable(value);
		sourceBox.setDisable(value);
		if (value) {
			updateAllButton.setDisable(true);
		}
	}

	private void refresh() {
		if (busy) {
			return;
		}
		setBusy(true);
		final Source source = source();
		summary.setText("Scanning " + source.dir() + "...");
		rows.clear();
		async(() -> scanSource(source), scanned -> {
			final List<LocalMod> mods = scanned.stream().map(Scanned::mod).toList();
			localMods = mods;
			missingDependencies = source.readOnly() ? List.of() : ModUpdater.findMissingDependencies(mods);
			final Map<LocalMod, ModRow> byMod = new java.util.IdentityHashMap<>();
			for (final Scanned item : scanned) {
				final ModRow row = new ModRow(item.mod(), item.origin());
				byMod.put(item.mod(), row);
				rows.add(row);
			}
			summary.setText("Checking " + mods.size() + " mod(s) for updates...");
			async(() -> {
				updater.checkForUpdates(mods, status -> Platform.runLater(() -> byMod.get(status.mod()).apply(status, source.readOnly())));
				return null;
			}, done -> finishCheck());
		});
	}

	/**
	 * The mods of a source. Joining a server loads the instance's own mods (and any other mod paths in
	 * clientsettings.json) alongside the ones the server made the game download, so a server view lists all.
	 */
	private List<Scanned> scanSource(final Source source) {
		final List<Scanned> result = new java.util.ArrayList<>();
		final Set<File> seen = new java.util.HashSet<>();
		addMods(result, seen, source.dir(), source.readOnly() ? "Server" : "Instance");
		if (source.readOnly()) {
			addMods(result, seen, instance.getModsPath(), "Instance");
			for (final String modPath : instance.getConfiguredModPaths(context.getObjectMapper())) {
				final File dir = new File(modPath);
				if (dir.isAbsolute()) {
					addMods(result, seen, dir, "Mod path");
				}
			}
		}
		return result;
	}

	private void addMods(final List<Scanned> result, final Set<File> seen, final File dir, final String origin) {
		File key = dir;
		try {
			key = dir.getCanonicalFile();
		} catch (java.io.IOException e) {
			// compare as given
		}
		if (dir.isDirectory() && seen.add(key)) {
			for (final LocalMod mod : updater.scan(dir)) {
				result.add(new Scanned(origin, mod));
			}
		}
	}

	private void exportManifest() {
		final FileChooser chooser = new FileChooser();
		chooser.setTitle("Export mods as " + ModManifest.MANIFEST_NAME);
		chooser.setInitialFileName(ModManifest.MANIFEST_NAME);
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
		final File home = new File(System.getProperty("user.home"));
		if (home.isDirectory()) {
			chooser.setInitialDirectory(home);
		}
		final File target = chooser.showSaveDialog(context.getStage());
		if (target == null) {
			return;
		}
		try {
			final ModManifest.Result result = ModManifest.export(localMods, instance.getVersion(), target,
					context.getObjectMapper());
			summary.setText("Exported " + result.mods() + " mod(s) to " + target + " (schema written next to it)"
					+ (result.skipped().isEmpty() ? "" : "; left out: " + String.join(", ", result.skipped())));
		} catch (java.io.IOException | RuntimeException e) {
			summary.setText("Could not export: " + e.getMessage());
		}
	}

	private void finishCheck() {
		setBusy(false);
		final long updates = rows.stream().filter(r -> r.updatable.get()).count();
		final int missing = missingDependencies.size();
		if (source().readOnly()) {
			summary.setText(rows.size() + " mod(s) installed by the server (managed by the game, read-only)");
		} else {
			summary.setText(rows.size() + " mod(s): " + updates + " update(s) available"
					+ (missing > 0 ? ", " + missing + " missing dependenc" + (missing == 1 ? "y" : "ies") : ""));
		}
		updateAllButton.setDisable(updates + missing == 0);
	}

	/** Resolves the requested mods plus dependencies, asks for confirmation, then installs. */
	private void install(final List<Request> requests) {
		if (busy || requests.isEmpty()) {
			return;
		}
		setBusy(true);
		summary.setText("Resolving mods and dependencies...");
		async(() -> updater.resolve(requests, localMods), plan -> {
			final List<PlanEntry> changes = plan.changes();
			if (changes.isEmpty()) {
				setBusy(false);
				summary.setText(plan.errors().isEmpty() ? "Nothing to install; everything is up to date."
						: "Could not resolve: " + plan.errors());
				return;
			}
			if (!confirm(plan)) {
				setBusy(false);
				summary.setText("No changes made.");
				return;
			}
			summary.setText("Installing " + changes.size() + " mod(s)...");
			async(() -> updater.apply(plan, source().dir()), messages -> {
				setBusy(false);
				refresh();
				summary.setText(String.join("; ", messages));
			});
		});
	}

	private boolean confirm(final Plan plan) {
		final StringBuilder text = new StringBuilder();
		for (final PlanEntry entry : plan.changes()) {
			text.append(entry.existing().isEmpty() ? "[+] Install " : "[~] Update ").append(entry.name());
			if (!entry.existing().isEmpty()) {
				text.append(" ").append(entry.existing().get(0).getVersion());
			}
			text.append(" -> ").append(entry.version());
			if (entry.requiredBy() != null) {
				text.append("  (dependency of ").append(entry.requiredBy()).append(")");
			}
			text.append('\n');
		}
		plan.errors().forEach((modid, error) -> text.append("[x] ").append(modid).append(": ").append(error).append('\n'));

		final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text.toString(), ButtonType.OK, ButtonType.CANCEL);
		alert.setHeaderText("Apply " + plan.changes().size() + " change(s)?");
		alert.setResizable(true);
		return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
	}
}
