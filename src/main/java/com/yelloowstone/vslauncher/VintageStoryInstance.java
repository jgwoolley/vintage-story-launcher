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
    private String name;
    private File runtimePath;
    private File dataPath;
    /**
     * Mod folders managed by the launcher and passed to the game with {@code --addModPath}. The first one is where
     * the Mods page installs and updates mods. Empty means just the default {@code <dataPath>/Mods}.
     */
    private List<File> modPaths;
    /** Extra command line arguments for the game, as typed (quotes group words). Null or empty for none. */
    private String launchArgs;
    /** A command put in front of the game, for example {@code gamemoderun}. Not used on macOS. */
    private String wrapper;
    /** Environment variables for the game as {@code KEY=value} words. Not used on macOS. */
    private String environment;
    /**
     * A dedicated server's data folder rather than a game client: only {@link #name} and {@link #dataPath} are used,
     * everything else comes from its {@code serverconfig.json} and save ({@link ServerConfig}). It is never launched.
     */
    private final boolean server;
    /** For a server: the client map (a {@code Maps/<savegame id>.db} of a client instance) last shown with it. */
    private File serverMapPath;

    @JsonCreator
    public VintageStoryInstance(
            @JsonProperty("name") final String name,
            @JsonProperty("runtimePath") final File runtimePath,
            @JsonProperty("dataPath") final File dataPath,
            // Older config.json files stored a single "modPath" (null = default); it becomes the first entry of modPaths.
            @JsonProperty("modPath") final File legacyModPath,
            @JsonProperty("modPaths") final List<File> modPaths,
            @JsonProperty("launchArgs") final String launchArgs,
            @JsonProperty("wrapper") final String wrapper,
            @JsonProperty("environment") final String environment,
            @JsonProperty("server") final Boolean server,
            @JsonProperty("serverMapPath") final File serverMapPath) {
        this.name = name;
        this.runtimePath = runtimePath;
        this.dataPath = dataPath;
        this.modPaths = new ArrayList<>();
        if (modPaths != null) {
            this.modPaths.addAll(modPaths);
        } else if (legacyModPath != null) {
            this.modPaths.add(legacyModPath);
        }
        this.launchArgs = launchArgs;
        this.wrapper = wrapper;
        this.environment = environment;
        // An instance saved before server support whose folder is a server's (serverconfig.json, no clientsettings.json)
        // is a server: opened as a client it has no player and no game settings.
        this.server = server != null ? server
                : dataPath != null && !new File(dataPath, "clientsettings.json").isFile() && ServerConfig.isServerDataPath(dataPath);
        this.serverMapPath = serverMapPath;
    }

    public VintageStoryInstance(final String name, final File runtimePath, final File dataPath, final File modPath) {
        this(name, runtimePath, dataPath, modPath, null, null, null, null, false, null);
    }

    /** A server instance: a dedicated server's data folder, shown with a client's map of its world. */
    public static VintageStoryInstance server(final String name, final File dataPath) {
        return new VintageStoryInstance(name, null, dataPath, null, null, null, null, null, true, null);
    }

    public boolean isServer() {
        return this.server;
    }

    public File getServerMapPath() {
        return this.serverMapPath;
    }

    public void setServerMapPath(final File serverMapPath) {
        this.serverMapPath = serverMapPath;
    }

    public VintageStoryInstance(final String name, final File runtimePath, final File dataPath) {
        this(name, runtimePath, dataPath, null);
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public File getDataPath() {
        return this.dataPath;
    }

    public void setDataPath(final File dataPath) {
        this.dataPath = dataPath;
    }

    public File getRuntimePath() {
        return runtimePath;
    }

    public void setRuntimePath(final File runtimePath) {
        this.runtimePath = runtimePath;
    }

    public List<File> getModPaths() {
        return new ArrayList<>(this.modPaths);
    }

    public void setModPaths(final List<File> modPaths) {
        this.modPaths = new ArrayList<>(modPaths);
    }

    public String getLaunchArgs() {
        return this.launchArgs;
    }

    public void setLaunchArgs(final String launchArgs) {
        this.launchArgs = launchArgs;
    }

    public String getWrapper() {
        return this.wrapper;
    }

    public void setWrapper(final String wrapper) {
        this.wrapper = wrapper;
    }

    public String getEnvironment() {
        return this.environment;
    }

    public void setEnvironment(final String environment) {
        this.environment = environment;
    }

    @JsonIgnore
    public LocalDateTime getLastOpen() {
        final Instant instant = Instant.ofEpochMilli(this.server ? getLastSaved() : new File(dataPath, "clientsettings.json").lastModified());
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
    
    /** A server's "last open": when any of its saves (or the live write-ahead log next to one) last changed. */
    private long getLastSaved() {
        long newest = new File(dataPath, ServerConfig.FILE_NAME).lastModified();
        final File[] files = new File(dataPath, "Saves").listFiles((dir, name) -> name.endsWith(".vcdbs") || name.endsWith(".vcdbs-wal"));
        if (files != null) {
            for (final File file : files) {
                newest = Math.max(newest, file.lastModified());
            }
        }
        return newest;
    }

    @JsonIgnore
    public String getVersion() {
        if (this.runtimePath == null) {
            return null;
        }
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
        if (this.server) {
            return; // a server instance is only looked at, never started
        }
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
    public File getDefaultModsPath() {
        return new File(this.dataPath, "Mods");
    }

    /** The mod folder the Mods page works on: the first launcher mod path, or the default Mods folder. */
    @JsonIgnore
    public File getModsPath() {
        return this.modPaths.isEmpty() ? getDefaultModsPath() : this.modPaths.get(0);
    }

    /** Every mod folder the launcher makes sure the game loads. */
    @JsonIgnore
    public List<File> getManagedModPaths() {
        return this.modPaths.isEmpty() ? List.of(getDefaultModsPath()) : getModPaths();
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
     * Arguments that make the game load this instance's mod folders (see {@link #getManagedModPaths()}) that
     * clientsettings.json does not already list. Appended to the game's own list, never replacing it.
     * The game crashes when {@code --addModPath} is repeated, so it is given once followed by every folder.
     */
    @JsonIgnore
    public List<String> getModPathArgs(final ObjectMapper objectMapper) {
        final List<String> configured = getConfiguredModPaths(objectMapper);
        final List<File> toAdd = new ArrayList<>();
        for (final File modsPath : getManagedModPaths()) {
            boolean skip = false;
            for (final String modPath : configured) {
                final File file = new File(modPath);
                if (file.isAbsolute() && isSameFile(file, modsPath)) {
                    skip = true;
                }
            }
            for (final File added : toAdd) {
                if (isSameFile(added, modsPath)) {
                    skip = true;
                }
            }
            if (!skip) {
                toAdd.add(modsPath);
            }
        }
        final List<String> result = new ArrayList<>();
        if (!toAdd.isEmpty()) {
            result.add("--addModPath");
            for (final File file : toAdd) {
                result.add(file.toString());
            }
        }
        return result;
    }

    private static boolean isSameFile(final File a, final File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    /** Splits a typed command line into words; single or double quotes group words. */
    public static List<String> splitWords(final String text) {
        final List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        final StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (final char c : text.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                started = true;
            } else if (Character.isWhitespace(c)) {
                if (started) {
                    words.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
            } else {
                current.append(c);
                started = true;
            }
        }
        if (started) {
            words.add(current.toString());
        }
        return words;
    }

    public void openWindows(final List<String> extraArgs) {
        // Fonts might not work see bash script...
        run(List.of(new File(this.getRuntimePath(), "Vintagestory").toString()), extraArgs, true);
    }

    public void openLinux(final List<String> extraArgs) {
        run(List.of(new File(this.getRuntimePath(), "run.sh").toString()), extraArgs, true);
    }

    public void openMac(final List<String> extraArgs) {
        // "open" starts the app through LaunchServices, so a wrapper command or environment would not reach the game.
        run(List.of("open", "-n", this.getRuntimePath().toString(), "--args"), extraArgs, false);
    }

    private void run(final List<String> program, final List<String> extraArgs, final boolean wrapAndEnvironment) {
        final List<String> args = new ArrayList<>();
        if (wrapAndEnvironment) {
            args.addAll(splitWords(this.wrapper));
        }
        args.addAll(program);
        args.add("--dataPath");
        args.add(this.getDataPath().toString());
        args.addAll(extraArgs);
        args.addAll(splitWords(this.launchArgs));
        final ProcessBuilder builder = new ProcessBuilder(args);
        if (wrapAndEnvironment) {
            for (final String word : splitWords(this.environment)) {
                final int eq = word.indexOf('=');
                if (eq > 0) {
                    builder.environment().put(word.substring(0, eq), word.substring(eq + 1));
                }
            }
        }
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
        if (this.server) {
            return null;
        }
        // No clientsettings.json yet (never run, or not a client folder) must not break the instance list
        final File settings = new File(dataPath, "clientsettings.json");
        if (!settings.isFile()) {
            return null;
        }
        try {
            final ClientSettingsFile file = getClientSettingsFile(objectMapper);
            return file.getStringSettings() == null ? null : file.getStringSettings().get("playername");
        } catch (RuntimeException e) {
            System.err.println("Could not read " + settings + ": " + e.getMessage());
            return null;
        }
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
        
        if (files == null) {
            return result; // no Maps folder (a server, or a client that never opened a world)
        }
        for(final File file: files) {
			result.add(new VintageStoryMap(file));
        }
                
        return result;

    }
}
