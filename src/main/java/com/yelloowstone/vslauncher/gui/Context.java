package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.MapRegistry;
import com.yelloowstone.vslauncher.SaveGameScanner;
import com.yelloowstone.vslauncher.ServerConfig;
import com.yelloowstone.vslauncher.Settings;
import com.yelloowstone.vslauncher.VintageStoryInstance;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Desktop;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Context {
    final ObjectMapper mapper;
    private final Stage stage;
    private final ObservableList<VintageStoryInstance> instances;
    private final VBox rootNode;
    private final File configHome;
    private final AudioClipProperty clickAudioClip;
    private final MapRegistry mapRegistry;
    private final Settings settings;
    private final File settingsFile;

    
    public Context(final Stage stage, final VBox rootNode) {
         this.mapper = JsonMapper.builder() // format-specific builders
                .build();

        this.stage = stage;
        this.rootNode = rootNode;
        this.instances = FXCollections.observableArrayList();

        // https://specifications.freedesktop.org/basedir/latest/
        this.configHome = new File(System.getProperty("user.home"), ".config/VSLauncher");
        configHome.mkdirs();

        final File saveFile = new File(configHome, "config.json");

        this.instances.addListener(new ListChangeListener<VintageStoryInstance>() {
            @Override
            public void onChanged(Change<? extends VintageStoryInstance> c) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(saveFile, instances);
            }
        });

        // One instance at a time: an entry that cannot be read is skipped instead of losing every instance. The next
        // save would drop it, so the file is copied to config.json.bak first.
        if (saveFile.exists()) {
            try {
                final List<VintageStoryInstance> loadedInstances = new ArrayList<>();
                boolean skipped = false;
                for (final JsonNode node : mapper.readTree(saveFile)) {
                    try {
                        loadedInstances.add(mapper.treeToValue(node, VintageStoryInstance.class));
                    } catch (RuntimeException e) {
                        skipped = true;
                        System.err.println("Skipping an instance in " + saveFile + " that could not be read: " + e.getMessage()
                                + "\n  " + node);
                    }
                }
                if (skipped) {
                    backup(saveFile);
                }
                this.instances.setAll(loadedInstances);
            } catch (RuntimeException e) {
                System.err.println("Could not read " + saveFile + ": " + e.getMessage());
                backup(saveFile);
            }
        }

        this.settingsFile = new File(configHome, "settings.json");
        this.settings = Settings.load(settingsFile, mapper);
        HumanTime.configure(settings);
        this.mapRegistry = new MapRegistry(new File(configHome, "maps.json"), mapper);
        this.clickAudioClip = new AudioClipProperty(Context.class.getResource("/click.wav"));
    }

    /** Keeps a copy of a config file that could not be read fully, before the launcher overwrites it. */
    private static void backup(final File file) {
        final File copy = new File(file.getPath() + ".bak");
        try {
            java.nio.file.Files.copy(file.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.err.println("Saved a copy of it as " + copy);
        } catch (IOException e) {
            System.err.println("Could not back up " + file + ": " + e.getMessage());
        }
    }

    public VBox getRootNode() {
        return rootNode;
    }

    public Settings getSettings() {
        return settings;
    }

    public void saveSettings() {
        settings.save(settingsFile, mapper);
    }

    public MapRegistry getMapRegistry() {
        return mapRegistry;
    }

    public ObservableList<VintageStoryInstance> getInstances() {
        return instances;
    }

    /** The game client instances (not servers): the ones that can be launched and have maps. */
    public List<VintageStoryInstance> getClientInstances() {
        return instances.stream().filter(instance -> !instance.isServer()).toList();
    }

    /**
     * Savegame identifier (lower case) -> name of the server instance running that world, read from each server's
     * saves. A client map with that identifier is that server's world. Reads the saves, so call it once per page.
     */
    public Map<String, String> getServerWorlds() {
        final Map<String, String> result = new HashMap<>();
        for (final VintageStoryInstance instance : instances) {
            if (!instance.isServer()) {
                continue;
            }
            try {
                for (final File save : ServerConfig.read(mapper, instance.getDataPath()).saves(instance.getDataPath())) {
                    final SaveGameScanner.SaveInfo info = SaveGameScanner.read(save);
                    if (info != null) {
                        result.putIfAbsent(info.id().toLowerCase(), instance.getName());
                    }
                }
            } catch (RuntimeException e) {
                System.err.println("Could not read the saves of server " + instance.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Call after editing an instance in place: saves config.json and notifies list listeners (the tray menu).
     * Replacing the element with itself fires a change, which is what the save listener reacts to.
     */
    public void instanceChanged(final VintageStoryInstance instance) {
        final int index = instances.indexOf(instance);
        if (index >= 0) {
            instances.set(index, instance);
        }
    }

    public ObservableList<File> getRuntimeDirs() {
        final Set<File> runtimeDirs = new HashSet<>();

        for(final var instance: this.getClientInstances()) {
            runtimeDirs.add(instance.getRuntimePath());
        }

        final File userHomePath = new File(System.getProperty("user.home"));

        // TODO: https://wiki.vintagestory.at/Vintagestory_folder

        final File localShareFile = new File(userHomePath, "/.local/share/");
        
        for(final File file: new File[] {
                new File("/Applications/Vintage Story.app"),
                new File(userHomePath, "/AppData/Roaming/Vintagestory"),
                new File(localShareFile, "/vintagestory"),
        }) {
        	if(file.exists()) {
                runtimeDirs.add(file);
        	}
        }
        
        final File[] backupPaths = localShareFile.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File file, String filename) {
				return filename.startsWith("vintagestory.bak");
			}
        	
        });
        
        if(backupPaths != null) {
            for(File file: backupPaths) {
                runtimeDirs.add(file);
            }
        }

        return FXCollections.observableArrayList(runtimeDirs);
    }

    public ObservableList<File> getDataDirs() {
        final Set<File> dataDirs = new HashSet<>();

        for(final var instance: this.getClientInstances()) {
            dataDirs.add(instance.getDataPath());
        }

        final File userHomePath = new File(System.getProperty("user.home"));

        for(final File file: new File[] {
                new File(userHomePath, "/Library/Application Support/VintagestoryData"),
                new File(userHomePath, "/.config/VintagestoryData/"),
                new File(userHomePath, "/.config/VintagestoryData2/"), // TODO: Remove
                new File(userHomePath, "/.var/app/at.vintagestory.VintageStory/config/VintagestoryData/"),
        }) {
            if(file.exists()) {
                dataDirs.add(file);
            }
        }

        final File vsInstancePath = new File(userHomePath, "/Documents/VSInstances");
        final File[] vsInstancePaths = vsInstancePath.listFiles();
        if(vsInstancePaths != null) {
            for(final File file: vsInstancePaths) {
                if(new File(file, "clientsettings.json").isFile()) {
                    dataDirs.add(file);
                }
            }
        }

        return FXCollections.observableArrayList(dataDirs);
    }

    public ObservableList<File> getModDirs() {
        final Set<File> modDirs = new HashSet<>();

        for(final var instance: this.getClientInstances()) {
            modDirs.addAll(instance.getManagedModPaths());
        }

        for(final File dataDir: this.getDataDirs()) {
            final File modsDir = new File(dataDir, "Mods");
            if(modsDir.isDirectory()) {
                modDirs.add(modsDir);
            }
        }

        return FXCollections.observableArrayList(modDirs);
    }

    public Stage getStage() {
        return stage;
    }

    public ObjectMapper getObjectMapper() {
        return mapper;
    }

    public File getConfigHome() {
        return configHome;
    }
    
    public AudioClipProperty getVolumeProperty() {
    	return this.clickAudioClip;
    }
    
    //TODO: Shouldn't be at both Context / Instance
    public static boolean isWindows() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("win");
    }

    public static boolean isMac() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("mac");
    }

    public static boolean isUnix() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
    }
    
    public static void openFile(File file) {
    	if(Context.isUnix()) {
    		try {
                // Use xdg-open for Linux
                new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
    	} else {
    		try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
    	}
    }
}
