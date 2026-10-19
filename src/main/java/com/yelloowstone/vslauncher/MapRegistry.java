package com.yelloowstone.vslauncher;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yelloowstone.vslauncher.SaveGameScanner.SaveInfo;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * What the launcher knows about maps, keyed by savegame identifier (the Maps/&lt;id&gt;.db file name):
 * the name you gave it and the server it was played on. Stored in config home as maps.json.
 */
public class MapRegistry {
    private static final Pattern CONNECTING = Pattern.compile("Connecting to (\\S+?)\\.\\.\\.");
    private static final Pattern MODS_BY_SERVER = Pattern.compile("ModsByServer/(\\S+)");

    public static class Entry {
        public String name;
        public String server;

        public Entry() {
        }
    }

    private final File file;
    private final ObjectMapper mapper;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public MapRegistry(final File file, final ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
        try {
            if (file.isFile()) {
                entries.putAll(mapper.readValue(file, new TypeReference<Map<String, Entry>>() {
                }));
            }
        } catch (RuntimeException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }
    }

    private static String key(final String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    /** The name you gave the map, or null. */
    public synchronized String getName(final String id) {
        final Entry entry = entries.get(key(id));
        return entry == null || entry.name == null || entry.name.isBlank() ? null : entry.name;
    }

    /** The server this map was last seen on, or null. */
    public synchronized String getServer(final String id) {
        final Entry entry = entries.get(key(id));
        return entry == null ? null : entry.server;
    }

    public synchronized void setName(final String id, final String name) {
        final Entry entry = entries.computeIfAbsent(key(id), k -> new Entry());
        entry.name = name == null || name.isBlank() ? null : name.strip();
        save();
    }

    public synchronized void setServer(final String id, final String server) {
        entries.computeIfAbsent(key(id), k -> new Entry()).server = server;
        save();
    }

    private void save() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, entries);
        } catch (RuntimeException e) {
            System.err.println("Could not write " + file + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ session tracking

    /** Modified time of every map file in a data folder, taken before the game starts. */
    public static Map<String, Long> snapshotMaps(final File dataPath) {
        final Map<String, Long> result = new LinkedHashMap<>();
        final File[] files = new File(dataPath, "Maps").listFiles((dir, name) -> name.endsWith(".db"));
        if (files != null) {
            for (final File f : files) {
                result.put(f.getName(), f.lastModified());
            }
        }
        return result;
    }

    /** Servers named in the data folder's client-main.log, which only holds the latest session. */
    static Set<String> serversInLog(final File dataPath) {
        final Set<String> servers = new LinkedHashSet<>();
        final File log = new File(new File(dataPath, "Logs"), "client-main.log");
        final List<String> lines;
        try {
            // Decode leniently: logs can contain bytes that are not valid UTF-8
            lines = new String(Files.readAllBytes(log.toPath()), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        } catch (Exception e) {
            return servers;
        }
        String connecting = null;
        for (final String line : lines) {
            final Matcher byServer = MODS_BY_SERVER.matcher(line);
            if (byServer.find()) {
                // "host-port"; this is the most precise form the log gives
                servers.add(byServer.group(1).replaceAll("/+$", ""));
                connecting = null;
                continue;
            }
            final Matcher matcher = CONNECTING.matcher(line);
            if (matcher.find()) {
                connecting = matcher.group(1);
                servers.add(connecting);
            }
        }
        return servers;
    }

    /**
     * After the game exits: maps that changed or appeared during the session belong to the server it
     * connected to. Single player maps (matching a save) and ambiguous sessions are left alone.
     */
    public void recordSession(final File dataPath, final Map<String, Long> before) {
        final Map<String, Long> after = snapshotMaps(dataPath);
        final Set<String> changed = new LinkedHashSet<>();
        after.forEach((name, modified) -> {
            final Long old = before.get(name);
            if (old == null || modified > old) {
                changed.add(name.substring(0, name.length() - 3));
            }
        });
        if (changed.isEmpty()) {
            return;
        }

        final Map<String, SaveInfo> saves = SaveGameScanner.scan(dataPath);
        changed.removeIf(id -> saves.containsKey(key(id)));
        final Set<String> servers = serversInLog(dataPath);
        // "host-port" entries are the precise ones; drop their bare "host" duplicates
        servers.removeIf(s -> servers.stream().anyMatch(other -> other.startsWith(s + "-")));
        if (changed.size() == 1 && servers.size() == 1) {
            setServer(changed.iterator().next(), servers.iterator().next());
        } else if (!changed.isEmpty()) {
            System.err.println("Could not tell which server " + changed + " belongs to (servers in log: " + servers + ")");
        }
    }
}
