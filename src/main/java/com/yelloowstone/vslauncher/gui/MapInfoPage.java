package com.yelloowstone.vslauncher.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.yelloowstone.vslauncher.MapCoordinates;
import com.yelloowstone.vslauncher.SaveGameScanner;
import com.yelloowstone.vslauncher.SaveGameStructures;
import com.yelloowstone.vslauncher.SaveSearch;
import com.yelloowstone.vslauncher.SaveGameStructures.Structure;
import com.yelloowstone.vslauncher.SaveGameStructures.Type;
import com.yelloowstone.vslauncher.SaveGameScanner.SaveInfo;
import com.yelloowstone.vslauncher.VintageStoryInstance;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MapInfoPage {
	private static final int RENDER_SIZE = 2048;
	/** Structures shown per page of the table (a big save has thousands). */
	private static final int PAGE_SIZE = 50;

	public static Button createBackButton(final Context context, final VintageStoryInstance instance) {
		final Button button = Icons.button("Back");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			MapsInfoPage.create(context, instance);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	/** The map with its go-to bar, status lines and the save's search table, as shown on a map page. */
	record MapSection(MapViewer viewer, Label status, List<Node> nodes, Consumer<VintageStoryMap> showMap) {
	}

	/**
	 * The map viewer, "Go to" bar and the table of what {@code save} records, marked on the map. {@code map} may be
	 * null (marks on an empty background) and can be swapped later with {@link MapSection#showMap}; {@code save} may be null
	 * (no table). Player names come from {@code dataPath}'s Playerdata.
	 */
	static MapSection createMapSection(final Context context, final VintageStoryMap map, final File dataPath,
			final SaveInfo save) {
		final MapCoordinates coordinates = new MapCoordinates(save != null ? save.origin() : MapCoordinates.DEFAULT_ORIGIN);
		final Label originLabel = new Label("Coordinates without = are relative to spawn at x =" + coordinates.getOriginX()
				+ " z =" + coordinates.getOriginZ() + (save == null ? " (assumed: middle of a default size world)" : ""));
		final Label hoverLabel = new Label(" ");
		final Label status = new Label(" ");
		final MapViewer viewer = new MapViewer(map, coordinates, hoverLabel::setText);
		viewer.setPrefHeight(400);
		VBox.setVgrow(viewer, Priority.ALWAYS);

		final TextField goField = new TextField();
		goField.setPromptText("Go to: x z (from spawn) or =x =z (absolute)");
		HBox.setHgrow(goField, Priority.ALWAYS);
		final Runnable go = () -> {
			try {
				final double[] target = coordinates.parse(goField.getText());
				viewer.goTo(target[0], target[1]);
				status.setText("Showing " + coordinates.describe(target[0], target[1]));
			} catch (IllegalArgumentException e) {
				status.setText(e.getMessage());
			}
		};
		goField.setOnAction(x -> go.run());
		final Button goButton = Icons.button("Go");
		goButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			go.run();
		});
		final Button fitButton = Icons.button("Fit");
		fitButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			viewer.fit();
		});
		final HBox goForm = new HBox(10, new Label("Go to"), goField, goButton, fitButton);

		// Without a map the background spans the marks: around spawn until the save is read, then all its rows.
		final List<Structure> rowsSoFar = new ArrayList<>();
		final boolean[] framed = { false };
		final Runnable frameBlank = () -> {
			int minX = coordinates.getOriginX() - 512, minZ = coordinates.getOriginZ() - 512;
			int maxX = coordinates.getOriginX() + 512, maxZ = coordinates.getOriginZ() + 512;
			for (final Structure s : rowsSoFar) {
				minX = Math.min(minX, s.x1() - 64);
				minZ = Math.min(minZ, s.z1() - 64);
				maxX = Math.max(maxX, s.x2() + 64);
				maxZ = Math.max(maxZ, s.z2() + 64);
			}
			viewer.showBlank(minX, minZ, maxX, maxZ);
		};
		final Consumer<List<Structure>> onRows = rows -> {
			rowsSoFar.clear();
			rowsSoFar.addAll(rows);
			// frame once (the first rows), so the later world scan does not move the view
			if (viewer.getMap() == null && !framed[0] && !rows.isEmpty()) {
				framed[0] = true;
				frameBlank.run();
			}
		};
		final Consumer<VintageStoryMap> showMap = shown -> {
			viewer.setMap(shown);
			if (shown == null) {
				frameBlank.run();
				status.setText("No client map of this world: only the marks are shown. Drag to pan, scroll to zoom.");
			} else {
				status.setText("Rendering map...");
				loadOverview(shown, viewer, status);
			}
		};
		showMap.accept(map);
		final VBox structures = createStructures(context, dataPath, save, coordinates, viewer, status, onRows);
		return new MapSection(viewer, status, List.of(goForm, originLabel, viewer, hoverLabel, status, structures), showMap);
	}

	/** Renders the overview off the UI thread (large maps are hundreds of MB) and hands it to the viewer. */
	private static void loadOverview(final VintageStoryMap map, final MapViewer viewer, final Label status) {
		final Task<VintageStoryMap.Image> task = new Task<>() {
			@Override
			protected VintageStoryMap.Image call() throws Exception {
				return map.render(RENDER_SIZE);
			}
		};
		task.setOnSucceeded(x -> {
			if (viewer.getMap() != map) {
				return; // another map was picked while this one rendered
			}
			final VintageStoryMap.Image image = task.getValue();
			if (image == null) {
				status.setText("This map has no explored tiles.");
				return;
			}
			viewer.setOverview(image);
			status.setText("Drag to pan, scroll to zoom.");
		});
		task.setOnFailed(x -> {
			if (viewer.getMap() == map) {
				status.setText("Could not render map: " + task.getException());
			}
		});
		final Thread thread = new Thread(task, "vs-map-render");
		thread.setDaemon(true);
		thread.start();
	}

	/** The "Same ..." button's text for a row: what it narrows the list to. */
	private static String sameLabel(final Structure s) {
		return switch (s.type()) {
		case STRUCTURE, STORY -> "Same Structure";
		case PLAYER, SPAWN, DEATH -> "Same Player";
		case WAYPOINT -> "Same Title";
		case ORE -> "Same Ore";
		case TRADER -> "Same Trader";
		case TRANSLOCATOR -> "Same Kind";
		};
	}

	/**
	 * What the map's single player save records, in one paged table with marks on the map: generated structures, story
	 * locations, players (position and spawn point), waypoints, last deaths, prospecting readings, translocators and
	 * traders. A search box and a checkbox per kind filter it; click a row to go there, "Same ..." / "Same Schematic"
	 * to list only those. A client's server map has no save here: the server keeps it (see {@link ServerMapPage}).
	 * {@code onRows} gets every row read so far, each time more arrive.
	 */
	private static VBox createStructures(final Context context, final File dataPath, final SaveInfo save, final MapCoordinates coordinates,
			final MapViewer viewer, final Label status, final Consumer<List<Structure>> onRows) {
		final Label title = new Label(save == null
				? "Structures: no save here for this map. A server world is searched from its server instance (Add Server, then Open Maps)."
				: "Structures: reading the save...");
		final VBox box = new VBox(6, title);
		if (save == null) {
			return box;
		}

		final ObservableList<Structure> all = FXCollections.observableArrayList();
		final FilteredList<Structure> filtered = new FilteredList<>(all);
		final SortedList<Structure> sorted = new SortedList<>(filtered);
		final ObservableList<Structure> page = FXCollections.observableArrayList();
		final int[] pageIndex = { 0 };
		// Row action filters: rows "the same" as this one and/or only this schematic (null = off).
		final Structure[] onlySame = { null };
		final String[] onlySchematic = { null };
		final Structure[] onlyPair = { null }; // a translocator: show it and its other end
		final boolean[] chunksDone = { false };

		final TextField search = new TextField();
		search.setPromptText("Search: part of a name or detail (shrine, trader, copper, a player) or a pattern like *vugs");
		HBox.setHgrow(search, Priority.ALWAYS);
		final CheckBox show = new CheckBox("Show on map");
		show.setSelected(true);
		show.selectedProperty().addListener((o, was, now) -> viewer.setShowStructures(now));
		final Map<Type, CheckBox> typeBoxes = new EnumMap<>(Type.class);
		final FlowPane typeBar = new FlowPane(12, 4, new Label("Show:"));
		typeBar.setAlignment(Pos.CENTER_LEFT);
		for (final Type type : Type.values()) {
			final CheckBox typeBox = new CheckBox(type.plural());
			typeBox.setSelected(true);
			typeBoxes.put(type, typeBox);
			typeBar.getChildren().add(typeBox);
		}
		final Label onlyLabel = new Label();
		final Button showAll = Icons.button("Show All");
		final HBox onlyBar = new HBox(10, onlyLabel, showAll);
		onlyBar.setAlignment(Pos.CENTER_LEFT);
		onlyBar.setVisible(false);
		onlyBar.setManaged(false);

		final Button previous = Icons.button("Previous");
		final Button next = Icons.button("Next");
		final Label pageLabel = new Label();
		final HBox pager = new HBox(10, previous, pageLabel, next);
		pager.setAlignment(Pos.CENTER);

		final Runnable showPage = () -> {
			final int pages = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);
			pageIndex[0] = Math.max(0, Math.min(pageIndex[0], pages - 1));
			final int from = pageIndex[0] * PAGE_SIZE;
			final int to = Math.min(sorted.size(), from + PAGE_SIZE);
			page.setAll(sorted.subList(from, to));
			pageLabel.setText(sorted.isEmpty() ? "Page 1 of 1"
					: "Page " + (pageIndex[0] + 1) + " of " + pages + "  (" + (from + 1) + "-" + to + " of " + sorted.size() + ")");
			previous.setDisable(pageIndex[0] == 0);
			next.setDisable(pageIndex[0] >= pages - 1);
		};
		sorted.addListener((ListChangeListener<Structure>) c -> showPage.run());
		previous.setOnAction(x -> {
			context.getVolumeProperty().play();
			pageIndex[0]--;
			showPage.run();
		});
		next.setOnAction(x -> {
			context.getVolumeProperty().play();
			pageIndex[0]++;
			showPage.run();
		});

		final Runnable refilter = () -> {
			final String text = search.getText();
			final Structure sameAs = onlySame[0];
			final String schematicFilter = onlySchematic[0];
			final Structure pair = onlyPair[0];
			final boolean rowFilter = sameAs != null || schematicFilter != null || pair != null;
			final Set<Type> shown = EnumSet.noneOf(Type.class);
			typeBoxes.forEach((type, typeBox) -> {
				if (typeBox.isSelected()) {
					shown.add(type);
				}
			});
			pageIndex[0] = 0;
			filtered.setPredicate(s -> shown.contains(s.type()) && s.matches(text) && (sameAs == null || SaveSearch.same(sameAs, s))
					&& (schematicFilter == null || schematicFilter.equals(s.schematic())) && (pair == null || SaveSearch.pairOf(pair, s)));
			viewer.setStructures((text == null || text.isBlank()) && !rowFilter && shown.size() == Type.values().length
					? all : List.copyOf(filtered));
			final Map<Type, Long> counts = all.stream().collect(Collectors.groupingBy(Structure::type,
					() -> new EnumMap<>(Type.class), Collectors.counting()));
			typeBoxes.forEach((type, typeBox) -> typeBox.setText(type.plural() + " (" + counts.getOrDefault(type, 0L) + ")"));
			title.setText("In this save: " + filtered.size() + (filtered.size() == all.size() ? "" : " of " + all.size())
					+ " (structures and translocators on explored land only"
					+ (chunksDone[0] ? ")" : "; still scanning the world for translocators and traders...)"));
			onlyLabel.setText(!rowFilter ? ""
					: "Only " + String.join(" and ", Stream.of(
							sameAs == null ? null
									: (sameAs.ofPlayer() ? "player" : sameAs.type().label().toLowerCase(Locale.ROOT)) + " "
											+ sameAs.kind(),
							schematicFilter == null ? null : "schematic " + schematicFilter,
							pair == null ? null
									: "the translocator at " + coordinates.describe(pair.centerX(), pair.centerZ())
											+ " and its other end")
							.filter(Objects::nonNull).toList()));
			onlyBar.setVisible(rowFilter);
			onlyBar.setManaged(rowFilter);
			showPage.run();
		};
		search.textProperty().addListener((o, was, now) -> refilter.run());
		typeBoxes.values().forEach(typeBox -> typeBox.selectedProperty().addListener((o, was, now) -> refilter.run()));
		showAll.setOnAction(x -> {
			context.getVolumeProperty().play();
			onlySame[0] = null;
			onlySchematic[0] = null;
			onlyPair[0] = null;
			refilter.run();
		});

		final TableView<Structure> table = new TableView<>(page);
		final TableColumn<Structure, String> typeColumn = new TableColumn<>("Type");
		typeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().type().label()));
		// each row shows its map mark
		typeColumn.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(final String item, final boolean empty) {
				super.updateItem(item, empty);
				final Structure structure = empty || getTableRow() == null ? null : getTableRow().getItem();
				setText(empty ? null : item);
				setGraphic(structure == null ? null : viewer.legend(structure));
			}
		});
		final TableColumn<Structure, String> kind = new TableColumn<>("Name");
		kind.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().kind()));
		final TableColumn<Structure, String> schematic = new TableColumn<>("Schematic / Details");
		schematic.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().schematic().isEmpty()
				? c.getValue().detail() : c.getValue().schematic()));
		final TableColumn<Structure, String> position = new TableColumn<>("Position (from spawn)");
		position.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.format(Locale.ROOT, "%d, %d, %d",
				Math.round(c.getValue().centerX() - coordinates.getOriginX()), c.getValue().y1(),
				Math.round(c.getValue().centerZ() - coordinates.getOriginZ()))));
		final TableColumn<Structure, Number> distance = new TableColumn<>("Distance from spawn");
		distance.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Math.round(Math.hypot(
				c.getValue().centerX() - coordinates.getOriginX(), c.getValue().centerZ() - coordinates.getOriginZ()))));
		final TableColumn<Structure, Void> actions = new TableColumn<>("Actions");
		actions.setSortable(false);
		actions.setMinWidth(400); // room for the buttons' text, no more
		actions.setMaxWidth(400);
		actions.setCellFactory(column -> new TableCell<>() {
			private final Button sameKind = Icons.button("Same Structure");
			private final Button sameSchematic = Icons.button("Same Schematic");
			private final Button copyTp = Icons.button("Copy /tp");
			private final HBox buttons = new HBox(6, copyTp, sameKind, sameSchematic);

			@Override
			protected void updateItem(final Void item, final boolean empty) {
				super.updateItem(item, empty);
				final Structure structure = empty || getTableRow() == null ? null : getTableRow().getItem();
				if (structure == null) {
					setGraphic(null);
					return;
				}
				sameKind.setText(sameLabel(structure));
				sameKind.setOnAction(x -> {
					context.getVolumeProperty().play();
					onlySame[0] = structure;
					refilter.run();
				});
				if (structure.type() == Type.TRANSLOCATOR) {
					// translocators have no schematic: the button lists this one and where it leads instead
					sameSchematic.setText("Show Pair");
					sameSchematic.setDisable(structure.link() == null);
					sameSchematic.setOnAction(x -> {
						context.getVolumeProperty().play();
						onlyPair[0] = structure;
						refilter.run();
					});
				} else {
					sameSchematic.setText("Same Schematic");
					// Rows without a schematic in their code have nothing to match on.
					sameSchematic.setDisable(structure.schematic().isEmpty());
					sameSchematic.setOnAction(x -> {
						context.getVolumeProperty().play();
						onlySchematic[0] = structure.schematic();
						refilter.run();
					});
				}
				copyTp.setOnAction(x -> {
					context.getVolumeProperty().play();
					final String command = SaveSearch.tpCommand(structure);
					final ClipboardContent content = new ClipboardContent();
					content.putString(command);
					Clipboard.getSystemClipboard().setContent(content);
					status.setText("Copied " + command + "   (paste it into the game's chat; needs teleport rights or creative)");
				});
				setGraphic(buttons);
			}
		});
		table.getColumns().addAll(List.of(typeColumn, kind, schematic, position, distance, actions));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		// Sort the whole (filtered) list, not just the rows on the current page, then show the page again.
		table.setSortPolicy(t -> {
			sorted.setComparator(t.getComparator());
			showPage.run();
			return true;
		});
		table.getSortOrder().add(distance);
		table.setPrefHeight(300);
		table.setPlaceholder(new Label("Nothing matches."));
		// set while a click on the map selects the row: mark it there, but leave the view where the user put it
		final boolean[] pickedOnMap = { false };
		table.getSelectionModel().selectedItemProperty().addListener((o, was, s) -> {
			if (s == null) {
				viewer.showLink(null, false);
				return;
			}
			if (pickedOnMap[0]) {
				viewer.mark(s.centerX(), s.centerZ());
			} else {
				viewer.goTo(s.centerX(), s.centerZ());
			}
			// a linked translocator: highlight the link and (unless clicked on the map) frame both ends
			viewer.showLink(s, !pickedOnMap[0]);
			final boolean point = s.y1() == s.y2();
			final int[] to = s.link();
			status.setText(s.type().label() + ": " + s.kind() + (s.detail().isEmpty() ? "" : " (" + s.detail() + ")") + " at "
					+ coordinates.describe(s.centerX(), s.centerZ()) + ", y " + (point ? s.y1() : s.y1() + "-" + s.y2())
					+ (to == null ? "" : String.format(Locale.ROOT, "   leads to %s, y %d (%d blocks away)",
							coordinates.describe(to[0] + 0.5, to[2] + 0.5), to[1],
							Math.round(Math.hypot(to[0] + 0.5 - s.centerX(), to[2] + 0.5 - s.centerZ()))))
					+ "   in game: " + SaveSearch.tpCommand(s));
		});

		// Clicking a mark on the map selects its row: turn to the page it is on, select it and scroll to it.
		viewer.setOnPick(picked -> {
			final int index = sorted.indexOf(picked);
			if (index < 0) {
				return; // not in the table (filtered out)
			}
			pageIndex[0] = index / PAGE_SIZE;
			showPage.run();
			final int row = index % PAGE_SIZE;
			pickedOnMap[0] = true;
			try {
				table.getSelectionModel().clearAndSelect(row);
			} finally {
				pickedOnMap[0] = false;
			}
			table.scrollTo(row);
		});

		final Map<String, String> names = SaveGameStructures.playerNames(context.getObjectMapper(), dataPath);
		final Runnable updatePlayers = () -> viewer.setPlayers(
				all.stream().filter(Structure::ofPlayer).map(Structure::kind).toList());
		// Quick part first (map regions, players, world data), then the slow full scan of the world's chunks.
		final Task<List<Structure>> task = new Task<>() {
			@Override
			protected List<Structure> call() {
				final List<Structure> rows = new ArrayList<>(SaveGameStructures.read(save.file()));
				rows.addAll(SaveGameStructures.readWorldData(save.file(), names));
				rows.addAll(SaveGameStructures.readPlayers(save.file(), names));
				return rows;
			}
		};
		final Task<List<Structure>> chunkTask = new Task<>() {
			@Override
			protected List<Structure> call() {
				return SaveGameStructures.readChunks(save.file());
			}
		};
		task.setOnSucceeded(x -> {
			all.addAll(task.getValue());
			onRows.accept(all);
			updatePlayers.run();
			table.sort();
			refilter.run();
			final Thread chunkThread = new Thread(chunkTask, "vs-save-chunks");
			chunkThread.setDaemon(true);
			chunkThread.start();
		});
		task.setOnFailed(x -> title.setText("Structures: could not read the save: " + task.getException()));
		chunkTask.setOnSucceeded(x -> {
			chunksDone[0] = true;
			all.addAll(chunkTask.getValue());
			onRows.accept(all);
			table.sort();
			refilter.run();
		});
		chunkTask.setOnFailed(x -> {
			chunksDone[0] = true;
			refilter.run();
			status.setText("Could not scan the world for translocators and traders: " + chunkTask.getException());
		});
		final Thread thread = new Thread(task, "vs-save-structures");
		thread.setDaemon(true);
		thread.start();

		showPage.run();
		final HBox searchBar = new HBox(10, new Label("Search"), search, show);
		searchBar.setAlignment(Pos.CENTER_LEFT);
		box.getChildren().addAll(searchBar, typeBar, onlyBar, table, pager);
		return box;
	}

	public static void create(final Context context, final VintageStoryInstance instance, final VintageStoryMap map) {
		final Map<String, SaveInfo> saves = SaveGameScanner.scan(instance.getDataPath());

		final Label fileLabel = new Label("Map File: " + map.getFile().getName());
		final Map<String, String> servers = context.getServerWorlds();
		final Label sourceLabel = new Label("Source: " + MapsInfoPage.describeSource(context, saves, servers, map));

		final TextField nameField = new TextField(context.getMapRegistry().getName(map.getId()));
		nameField.setPromptText(MapsInfoPage.displayName(context, saves, servers, map));
		HBox.setHgrow(nameField, Priority.ALWAYS);
		final Button saveButton = Icons.button("Save Name");
		saveButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			context.getMapRegistry().setName(map.getId(), nameField.getText());
			create(context, instance, map);
		});
		final HBox nameForm = new HBox(10, new Label("Name"), nameField, saveButton);

		final SaveInfo save = saves.get(map.getId().toLowerCase());
		final MapSection section = createMapSection(context, map, instance.getDataPath(), save);

		context.getRootNode().getChildren().clear();
		context.getRootNode().getChildren().addAll(fileLabel, sourceLabel, nameForm);
		context.getRootNode().getChildren().addAll(section.nodes());
		context.getRootNode().getChildren().add(createBackButton(context, instance));
	}
}
