package com.yelloowstone.vslauncher.mods;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes the mods you have as a vs-manifest.json (validated by vs-manifest.schema.json, which is written
 * next to it): the server version plus every mod pinned to its installed version.
 */
public class ModManifest {
    public static final String MANIFEST_NAME = "vs-manifest.json";
    public static final String SCHEMA_NAME = "vs-manifest.schema.json";

    // Mirrors the schema, so an export never fails validation
    private static final Pattern SERVER_VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?$");
    private static final Pattern MODID = Pattern.compile("^[A-Za-z0-9_]+$");

    /** What was written and anything that had to be left out. */
    public record Result(int mods, List<String> skipped) {
    }

    /**
     * @param gameVersion the instance's game version, e.g. "1.22.7"
     * @throws IllegalArgumentException if the game version is missing or not a valid server version
     */
    public static Result export(final List<LocalMod> mods, final String gameVersion, final File target,
            final ObjectMapper mapper) throws IOException {
        if (gameVersion == null || !SERVER_VERSION.matcher(gameVersion).matches()) {
            throw new IllegalArgumentException("The game version of this instance is unknown (" + gameVersion
                    + "), so the manifest's serverVersion can't be set.");
        }

        final List<String> skipped = new ArrayList<>();
        final Map<String, Map<String, String>> byModid = new LinkedHashMap<>();
        mods.stream().filter(m -> m.getError() == null)
                .sorted(Comparator.comparing(m -> m.getModid().toLowerCase(Locale.ROOT)))
                .forEach(mod -> {
                    if (!MODID.matcher(mod.getModid()).matches()) {
                        skipped.add(mod.getName() + " (modid '" + mod.getModid() + "' is not valid in the schema)");
                    } else if (byModid.putIfAbsent(mod.getModid().toLowerCase(Locale.ROOT),
                            entry(mod.getModid(), mod.getVersion())) != null) {
                        skipped.add(mod.getName() + " (a mod with id '" + mod.getModid() + "' is already listed)");
                    }
                });

        final Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("$schema", "./" + SCHEMA_NAME);
        // Pre-release versions (1.22.0-rc.1) come from the unstable channel
        manifest.put("serverBranch", gameVersion.contains("-") ? "unstable" : "stable");
        manifest.put("serverVersion", gameVersion);
        manifest.put("mods", new ArrayList<>(byModid.values()));

        mapper.writerWithDefaultPrettyPrinter().writeValue(target, manifest);
        writeSchema(new File(target.getAbsoluteFile().getParentFile(), SCHEMA_NAME));
        return new Result(byModid.size(), skipped);
    }

    private static Map<String, String> entry(final String modid, final String version) {
        final Map<String, String> entry = new LinkedHashMap<>();
        entry.put("modid", modid);
        entry.put("version", version);
        return entry;
    }

    private static void writeSchema(final File target) throws IOException {
        try (InputStream in = ModManifest.class.getResourceAsStream("/" + SCHEMA_NAME)) {
            if (in == null) {
                throw new IOException(SCHEMA_NAME + " is missing from the launcher");
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
