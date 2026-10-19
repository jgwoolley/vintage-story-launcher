package com.yelloowstone.vslauncher;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the single player saves of a data folder and the savegame identifier of each. The client
 * names its map cache after that identifier, so it ties a Maps/&lt;id&gt;.db file to a save.
 */
public class SaveGameScanner {
    private static final int WORLD_NAME_FIELD = 13;
    private static final int SAVEGAME_IDENTIFIER_FIELD = 34;
    private static final int MAP_SIZE_X_FIELD = 1;
    private static final int MAP_SIZE_Z_FIELD = 3;
    private static final int DEFAULT_SPAWN_FIELD = 40;
    private static final Pattern GUID = Pattern
            .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** A single player save: its file (Saves/x.vcdbs) and the world name stored inside it. */
    public record SaveInfo(String id, File file, String worldName, Integer mapSizeX, Integer mapSizeZ,
            Integer spawnX, Integer spawnZ) {
        /** Coordinates the game shows are relative to this: the default spawn, else the middle of the world. */
        public int[] origin() {
            if (spawnX != null && spawnZ != null) {
                return new int[] { spawnX, spawnZ };
            }
            if (mapSizeX != null && mapSizeZ != null && mapSizeX > 0 && mapSizeZ > 0) {
                return new int[] { mapSizeX / 2, mapSizeZ / 2 };
            }
            return MapCoordinates.DEFAULT_ORIGIN;
        }

        public String displayName() {
            final String fileName = file.getName();
            final String base = fileName.endsWith(".vcdbs") ? fileName.substring(0, fileName.length() - 6) : fileName;
            return worldName != null && !worldName.isBlank() && !worldName.equals(base) ? base + " (" + worldName + ")"
                    : base;
        }
    }

    /** Savegame identifier (lower case) to save, for every readable save in {@code <dataPath>/Saves}. */
    public static Map<String, SaveInfo> scan(final File dataPath) {
        final Map<String, SaveInfo> result = new HashMap<>();
        final File[] files = new File(dataPath, "Saves").listFiles((dir, name) -> name.endsWith(".vcdbs"));
        if (files == null) {
            return result;
        }
        for (final File file : files) {
            final SaveInfo info = read(file);
            if (info != null) {
                result.put(info.id().toLowerCase(), info);
            }
        }
        return result;
    }

    static SaveInfo read(final File file) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toURI() + "?mode=ro&immutable=1");
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT data FROM gamedata LIMIT 1")) {
            if (!rs.next()) {
                return null;
            }
            return parse(file, rs.getBytes(1));
        } catch (Exception e) {
            System.err.println("Could not read save " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    static SaveInfo parse(final File file, final byte[] data) {
        if (data == null) {
            return null;
        }
        String id = null;
        String worldName = null;
        Integer sizeX = null, sizeZ = null, spawnX = null, spawnZ = null;
        try {
            for (final Protobuf.Field field : Protobuf.parse(data)) {
                if (field.bytes() == null) {
                    if (field.id() == MAP_SIZE_X_FIELD) {
                        sizeX = (int) field.number();
                    } else if (field.id() == MAP_SIZE_Z_FIELD) {
                        sizeZ = (int) field.number();
                    }
                    continue;
                }
                if (field.id() == DEFAULT_SPAWN_FIELD) {
                    try {
                        for (final Protobuf.Field spawn : Protobuf.parse(field.bytes())) {
                            if (spawn.bytes() == null && spawn.id() == 1) {
                                spawnX = (int) spawn.number();
                            } else if (spawn.bytes() == null && spawn.id() == 3) {
                                spawnZ = (int) spawn.number();
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        spawnX = spawnZ = null;
                    }
                } else if (field.id() == SAVEGAME_IDENTIFIER_FIELD) {
                    id = field.string();
                } else if (field.id() == WORLD_NAME_FIELD) {
                    worldName = field.string();
                }
            }
        } catch (IllegalArgumentException e) {
            // fall through to the text search below
        }
        if (id == null || !GUID.matcher(id).matches()) {
            // Unexpected layout: fall back to the first GUID-looking string in the blob
            final Matcher matcher = GUID.matcher(new String(data, StandardCharsets.ISO_8859_1));
            id = matcher.find() ? matcher.group() : null;
        }
        return id == null ? null : new SaveInfo(id, file, worldName, sizeX, sizeZ, spawnX, spawnZ);
    }
}
