package com.yelloowstone.vslauncher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What a dedicated server's data folder says about itself in {@code serverconfig.json}: its name, port and the world it
 * runs ({@code WorldConfig.SaveFileLocation}). A server instance needs nothing else from the user.
 */
public record ServerConfig(String serverName, Integer port, String saveFileLocation) {
    public static final String FILE_NAME = "serverconfig.json";

    /** True when the folder looks like a server's data folder (it has a serverconfig.json). */
    public static boolean isServerDataPath(final File dataPath) {
        return dataPath != null && new File(dataPath, FILE_NAME).isFile();
    }

    /** Reads {@code <dataPath>/serverconfig.json}; every field is null when it is missing or unreadable. */
    public static ServerConfig read(final ObjectMapper mapper, final File dataPath) {
        final File file = new File(dataPath, FILE_NAME);
        if (!file.isFile()) {
            return new ServerConfig(null, null, null);
        }
        try {
            final JsonNode root = mapper.readTree(file);
            final JsonNode port = root.path("Port");
            return new ServerConfig(root.path("ServerName").asString(null), port.isNumber() ? port.asInt() : null,
                    root.path("WorldConfig").path("SaveFileLocation").asString(null));
        } catch (RuntimeException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
            return new ServerConfig(null, null, null);
        }
    }

    /**
     * The save the server runs. {@code SaveFileLocation} is an absolute path on the server's machine, which differs
     * when the folder is seen from elsewhere (a Docker volume, a copy): then the file of that name in
     * {@code <dataPath>/Saves} is used. Null when neither exists.
     */
    public File saveFile(final File dataPath) {
        if (saveFileLocation == null || saveFileLocation.isBlank()) {
            final File fallback = new File(new File(dataPath, "Saves"), "default.vcdbs");
            return fallback.isFile() ? fallback : null;
        }
        final File configured = new File(saveFileLocation);
        if (configured.isAbsolute() && configured.isFile()) {
            return configured;
        }
        // The name part, with either separator (the config may come from Windows)
        final String name = saveFileLocation.substring(Math.max(saveFileLocation.lastIndexOf('/'),
                saveFileLocation.lastIndexOf('\\')) + 1);
        for (final File candidate : new File[] { new File(dataPath, saveFileLocation),
                new File(new File(dataPath, "Saves"), name) }) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    /** Every save in {@code <dataPath>/Saves}, the configured one first, then newest first. */
    public List<File> saves(final File dataPath) {
        final List<File> result = new ArrayList<>();
        final File configured = saveFile(dataPath);
        if (configured != null) {
            result.add(configured);
        }
        final File[] files = new File(dataPath, "Saves").listFiles((dir, name) -> name.endsWith(".vcdbs"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (final File file : files) {
                if (configured == null || !file.getAbsoluteFile().equals(configured.getAbsoluteFile())) {
                    result.add(file);
                }
            }
        }
        return result;
    }
}
