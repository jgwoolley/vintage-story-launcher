package com.yelloowstone.vslauncher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tools.jackson.databind.ObjectMapper;

public class VintageStoryInstance {
    private final String name;
    private final File runtimePath;
    private final File dataPath;
    /** Null for instances saved before the mod path was configurable; see {@link #getModsPath()}. */
    private final File modPath;

    @JsonCreator
    public VintageStoryInstance(
            @JsonProperty("name") final String name,
            @JsonProperty("runtimePath") final File runtimePath,
            @JsonProperty("dataPath") final File dataPath,
            @JsonProperty("modPath") final File modPath) {
        this.name = name;
        this.runtimePath = runtimePath;
        this.dataPath = dataPath;
        this.modPath = modPath;
    }

    public VintageStoryInstance(final String name, final File runtimePath, final File dataPath) {
        this(name, runtimePath, dataPath, null);
    }

    @JsonProperty("modPath")
    public File getModPath() {
        return this.modPath;
    }

    public String getName() {
        return this.name;
    }

    public File getDataPath() {
        return this.dataPath;
    }

    public File getRuntimePath() {
        return runtimePath;
    }
    
    @JsonIgnore
    public LocalDateTime getLastOpen() {
        final File src = new File(dataPath, "clientsettings.json");
        final Instant instant = Instant.ofEpochMilli(src.lastModified());
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
    
    @JsonIgnore
    public String getVersion() {
        final File assetsPath = new File(this.runtimePath,"assets");
        if(!assetsPath.isDirectory()) {
            return null;
        }

        final File[] files = assetsPath.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("version-") && name.endsWith(".txt");
            }
        });
        if(files == null || files.length == 0) {
            return null;
        }

        return files[0].getName().substring("version-".length(), files[0].getName().length() - ".txt".length());
    }

    @Override
    public String toString() {
        return this.getName();
    }

    @JsonIgnore
    public static boolean isWindows() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("win");
    }

    @JsonIgnore
    public static boolean isMac() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("mac");
    }

    @JsonIgnore
    public static boolean isUnix() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
    }

    public void open(final ObjectMapper objectMapper, final MapRegistry maps) {
        final List<String> modPathArgs = getModPathArgs(objectMapper);
        // The launch methods wait for the game to exit, so run them off the UI thread or the
        // launcher window stops responding for as long as the game is open.
        final Thread thread = new Thread(() -> {
            final Map<String, Long> mapsBefore = MapRegistry.snapshotMaps(dataPath);
            if (isWindows()) {
                openWindows(modPathArgs);
            } else if (isMac()) {
                openMac(modPathArgs);
            } else if (isUnix()) {
                openLinux(modPathArgs);
            } else {
                System.out.println("Detected: Unknown OS");
            }
            // The launch methods return once the game has exited: note which server any new map belongs to
            maps.recordSession(dataPath, mapsBefore);
        }, "vs-game-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    @JsonIgnore
    public File getModsPath() {
        return this.modPath != null ? this.modPath : new File(this.dataPath, "Mods");
    }

    /**
     * The "modPaths" entry of clientsettings.json. Vintage Story stores the Mods folder
     * as an absolute path, so a clientsettings.json copied from another data folder or
     * synced from another computer (e.g. a different user name) can point somewhere else.
     */
    @JsonIgnore
    public List<String> getConfiguredModPaths(final ObjectMapper objectMapper) {
        try {
            final ClientSettingsFile settings = getClientSettingsFile(objectMapper);
            if (settings == null || settings.getStringListSettings() == null) {
                return Collections.emptyList();
            }
            final List<String> modPaths = settings.getStringListSettings().get("modPaths");
            return modPaths == null ? Collections.emptyList() : modPaths;
        } catch (RuntimeException e) {
            System.err.println("Could not read modPaths from clientsettings.json: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Arguments that make the game load this instance's Mods folder when clientsettings.json
     * does not already list it.
     */
    @JsonIgnore
    public List<String> getModPathArgs(final ObjectMapper objectMapper) {
        final File modsPath = getModsPath();
        for (final String modPath : getConfiguredModPaths(objectMapper)) {
            final File file = new File(modPath);
            if (file.isAbsolute() && isSameFile(file, modsPath)) {
                return Collections.emptyList();
            }
        }

        final List<String> result = new ArrayList<>();
        result.add("--addModPath");
        result.add(modsPath.toString());
        return result;
    }

    private static boolean isSameFile(final File a, final File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    public void openWindows(final List<String> extraArgs) {
    	// Fonts might not work see bash script...
        final List<String> args = new ArrayList<>(List.of(
                new File(this.getRuntimePath(), "Vintagestory").toString(),
                "--dataPath",
                this.getDataPath().toString()));
        args.addAll(extraArgs);
        final ProcessBuilder builder = new ProcessBuilder(args);
        builder.inheritIO(); // an unread output pipe fills up and eventually stalls the game
        try {
            final Process process = builder.start();
            final int exitCode = process.waitFor();
            System.out.println("Process Exit Code: " +  exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void openLinux(final List<String> extraArgs) {
        final List<String> args = new ArrayList<>(List.of(
                new File(this.getRuntimePath(), "run.sh").toString(),
                "--dataPath",
                this.getDataPath().toString()));
        args.addAll(extraArgs);
        final ProcessBuilder builder = new ProcessBuilder(args);
        builder.inheritIO(); // an unread output pipe fills up and eventually stalls the game
        try {
            final Process process = builder.start();
            final int exitCode = process.waitFor();
            System.out.println("Process Exit Code: " +  exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void openMac(final List<String> extraArgs) {
        final List<String> args = new ArrayList<>(List.of(
                "open",
                "-n",
                this.getRuntimePath().toString(),
                "--args",
                "--dataPath",
                this.getDataPath().toString()));
        args.addAll(extraArgs);
        final ProcessBuilder builder = new ProcessBuilder(args);
        builder.inheritIO(); // an unread output pipe fills up and eventually stalls the game
        try {
            final Process process = builder.start();
            final int exitCode = process.waitFor();
            System.out.println("Process Exit Code: " + exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void copy(VintageStoryInstance other) {
        final File sourceMods = this.getModsPath();
        sourceMods.mkdirs();
        final File destinationMods = other.getModsPath();
        destinationMods.mkdirs();

        final File[] sourceModFiles = sourceMods.listFiles((file, name) -> name.endsWith(".zip"));
        if(sourceModFiles == null) {
            System.err.println("No mods?!?!");
            return;
        }

        System.out.println(sourceModFiles.length);


        for(final File file: sourceModFiles) {
            final Path destMod = new File(destinationMods, file.getName()).toPath();
            try {
                Files.copy(file.toPath(), destMod, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Copied " + destMod.toString());
        }
    }

    @JsonIgnore
    public ClientSettingsFile getClientSettingsFile(ObjectMapper objectMapper) {
        final File src = new File(dataPath, "clientsettings.json");
        return objectMapper.readValue(src, ClientSettingsFile.class);
    }

    @JsonIgnore
    public String getPlayer(ObjectMapper objectMapper) {
        return getClientSettingsFile(objectMapper).getStringSettings().get("playername");
    }
    
    @JsonIgnore
    public ObservableList<VintageStoryMap> getMaps() {
        final ObservableList<VintageStoryMap> result = FXCollections.observableArrayList();
        final File mapsPath = new File(this.getDataPath(), "Maps");
        
        final File[] files = mapsPath.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File file, String name) {
				return name.endsWith(".db");
			}
        });
        
        for(final File file: files) {
			result.add(new VintageStoryMap(file));
        }
                
        return result;

    }
}
