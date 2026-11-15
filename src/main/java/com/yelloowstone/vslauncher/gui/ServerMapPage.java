package com.yelloowstone.vslauncher.gui;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.yelloowstone.vslauncher.SaveGameScanner;
import com.yelloowstone.vslauncher.SaveGameScanner.SaveInfo;
import com.yelloowstone.vslauncher.ServerConfig;
import com.yelloowstone.vslauncher.VintageStoryInstance;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A server instance's world: the server's save (structures, schematics, players, waypoints, translocators, traders...)
 * searched and marked over a map one of your client instances has of the same world. A client names that map after
 * the world's savegame identifier, so it is found without any setup; names are given on the client's own map page.
 */
public class ServerMapPage {

	/** A client instance's map of a world; {@code map} null is "no map" (marks only). */
	public record ClientMap(VintageStoryInstance instance, VintageStoryMap map, String name) {
		@Override
		public String toString() {
			if (map == null) {
				return "None (marks only)";
			}
			final LocalDateTime played = LocalDateTime.ofInstant(Instant.ofEpochMilli(map.getFile().lastModified()),
					ZoneId.systemDefault());
			return instance.getName() + ": " + (name != null ? "\"" + name + "\"" : map.getFile().getName()) + ", "
					+ MapsInfoPage.formatSize(map.getFile().length()) + ", played " + HumanTime.describe(played);
		}
	}

	/** Every client instance's map of world {@code id}, biggest (most explored) first. */
	public static List<ClientMap> clientMaps(final Context context, final String id) {
		final List<ClientMap> result = new ArrayList<>();
		for (final VintageStoryInstance instance : context.getClientInstances()) {
			for (final VintageStoryMap map : instance.getMaps()) {
				if (map.getId().equalsIgnoreCase(id)) {
					result.add(new ClientMap(instance, map, context.getMapRegistry().getName(map.getId())));
				}
			}
		}
		result.sort(Comparator.comparingLong((ClientMap m) -> m.map().getFile().length()).reversed());
		return result;
	}

	private static Button backButton(final Context context, final VintageStoryInstance server) {
		final Button button = Icons.button("Back");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			InstanceInfoPage.create(context, server);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}

	/** Opens the world the server runs (its serverconfig.json's save, else its newest save). */
	public static void create(final Context context, final VintageStoryInstance server) {
		create(context, server, null);
	}

	/** Opens {@code saveFile} of the server, or its configured world when null. */
	public static void create(final Context context, final VintageStoryInstance server, final File saveFile) {
		final File dataPath = server.getDataPath();
		final ServerConfig config = ServerConfig.read(context.getObjectMapper(), dataPath);
		final List<SaveInfo> saves = new ArrayList<>();
		for (final File file : config.saves(dataPath)) {
			final SaveInfo info = SaveGameScanner.read(file);
			if (info != null) {
				saves.add(info);
			}
		}

		final Label serverLabel = new Label("Server: " + server.getName()
				+ (config.serverName() != null && !config.serverName().equals(server.getName()) ? " (" + config.serverName() + ")" : "")
				+ (config.port() == null ? "" : ", port " + config.port()) + "   " + dataPath);
		context.getRootNode().getChildren().clear();
		if (saves.isEmpty()) {
			context.getRootNode().getChildren().addAll(serverLabel,
					new Label("No readable save in " + new File(dataPath, "Saves") + "."), backButton(context, server));
			return;
		}
		final SaveInfo save = saves.stream().filter(s -> saveFile != null && s.file().equals(saveFile)).findFirst()
				.orElse(saves.get(0));

		// Pick the world when the folder has several saves (the configured one is first)
		final ComboBox<SaveInfo> worldBox = new ComboBox<>(FXCollections.observableArrayList(saves));
		worldBox.setValue(save);
		worldBox.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(worldBox, Priority.ALWAYS);
		worldBox.setDisable(saves.size() < 2);
		worldBox.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(final SaveInfo info) {
				if (info == null) {
					return "";
				}
				final LocalDateTime saved = LocalDateTime.ofInstant(Instant.ofEpochMilli(Math.max(info.file().lastModified(),
						new File(info.file().getPath() + "-wal").lastModified())), ZoneId.systemDefault());
				return info.displayName() + "   (" + info.id() + ", saved " + HumanTime.describe(saved) + ")";
			}

			@Override
			public SaveInfo fromString(final String text) {
				return null;
			}
		});
		worldBox.valueProperty().addListener((o, was, now) -> {
			if (now != null && now != save) {
				create(context, server, now.file());
			}
		});
		final Button refreshButton = Icons.button("Refresh");
		refreshButton.setOnAction(x -> {
			context.getVolumeProperty().play();
			create(context, server, save.file());
		});
		final HBox worldForm = new HBox(10, new Label("World"), worldBox, refreshButton);

		// The client maps of this world, plus "none"; the last pick is remembered per server
		final List<ClientMap> choices = new ArrayList<>(clientMaps(context, save.id()));
		final ClientMap none = new ClientMap(null, null, null);
		choices.add(none);
		final ClientMap initial = choices.stream()
				.filter(c -> c.map() != null && c.map().getFile().equals(server.getServerMapPath())).findFirst()
				.orElse(choices.get(0));
		final ComboBox<ClientMap> mapBox = new ComboBox<>(FXCollections.observableArrayList(choices));
		mapBox.setValue(initial);
		mapBox.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(mapBox, Priority.ALWAYS);
		final HBox mapForm = new HBox(10, new Label("Client Map"), mapBox);
		final Label mapHint = new Label();
		mapHint.setWrapText(true);
		final java.util.function.Consumer<ClientMap> describe = choice -> mapHint.setText(choices.size() == 1
				? "No client instance has a map of this world yet: join the server from one (the map is saved as "
						+ save.id() + ".db) to see the land under the marks."
				: choice.map() == null ? "Showing only the marks; pick a client map to see the land under them."
				: "Map from " + choice.map().getFile() + ". Marks the server knows of outside your explored land sit on the dark background.");
		describe.accept(initial);

		final MapInfoPage.MapSection section = MapInfoPage.createMapSection(context, initial.map(), dataPath, save);
		mapBox.valueProperty().addListener((o, was, now) -> {
			if (now == null) {
				return;
			}
			context.getVolumeProperty().play();
			describe.accept(now);
			section.showMap().accept(now.map());
			server.setServerMapPath(now.map() == null ? null : now.map().getFile());
			context.instanceChanged(server);
		});

		context.getRootNode().getChildren().addAll(serverLabel, worldForm, mapForm, mapHint);
		context.getRootNode().getChildren().addAll(section.nodes());
		context.getRootNode().getChildren().add(backButton(context, server));
	}
}
