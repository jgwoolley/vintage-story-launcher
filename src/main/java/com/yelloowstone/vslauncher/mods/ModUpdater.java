package com.yelloowstone.vslauncher.mods;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.yelloowstone.vslauncher.mods.ModDbClient.ModDbException;
import com.yelloowstone.vslauncher.mods.ModDbClient.ModRecord;
import com.yelloowstone.vslauncher.mods.ModDbClient.Release;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Scans a Mods folder, checks the mod database for newer releases and installs updates
 * together with the dependencies they need.
 */
public class ModUpdater {
    private static final int MAX_WORKERS = 8;

    /** Dependencies that ship with the game and are not on the mod database. */
    private static final Set<String> BUILT_IN = Set.of("game", "survival", "creative", "essentials");

    /** Result of checking one local mod against the mod database. */
    public record ModStatus(LocalMod mod, Release latest, String error) {
        public boolean isUpdateAvailable() {
            return error == null && latest != null && latest.version() != null
                    && !latest.version().equals(mod.getVersion());
        }
    }

    /** A modid that should be installed or updated, and what asked for it (null if the user did). */
    public record Request(String modid, String requiredBy) {
    }

    /** One resolved mod of an install plan. {@code data} is null when it is already at the latest version. */
    public record PlanEntry(String modid, String name, String version, Release release, byte[] data,
            List<LocalMod> existing, String requiredBy) {
        public boolean isChange() {
            return data != null;
        }
    }

    public record Plan(List<PlanEntry> entries, Map<String, String> errors) {
        public List<PlanEntry> changes() {
            return entries.stream().filter(PlanEntry::isChange).toList();
        }
    }

    private final ModDbClient client = new ModDbClient();
    // modinfo.json files written by hand are often not strict JSON
    private final ObjectMapper lenientMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_TRAILING_COMMA,
                    JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES, JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    // ---------------------------------------------------------------- scanning

    public List<LocalMod> scan(final File modsDir) {
        final List<LocalMod> mods = new ArrayList<>();
        final File[] files = modsDir.listFiles();
        if (files == null) {
            return mods;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (final File file : files) {
            final LocalMod mod = scanEntry(file);
            if (mod != null) {
                mods.add(mod);
            }
        }
        return mods;
    }

    private LocalMod scanEntry(final File file) {
        final String entry = file.getName();
        final String lower = entry.toLowerCase(Locale.ROOT);
        String raw;
        byte[] icon;
        try {
            if (file.isFile() && lower.endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(file)) {
                    raw = readZipText(zip, "modinfo.json");
                    icon = readZipBytes(zip, "modicon.png");
                }
                if (raw == null) {
                    return new LocalMod(entry, "no modinfo.json found in zip");
                }
            } else if (file.isDirectory()) {
                raw = readDirText(file, "modinfo.json");
                if (raw == null) {
                    return null;
                }
                icon = readDirBytes(file, "modicon.png");
            } else if (file.isFile() && lower.endsWith(".dll")) {
                return new LocalMod(entry, "compiled mod without modinfo.json; skipped");
            } else {
                return null;
            }
        } catch (IOException | RuntimeException e) {
            return new LocalMod(entry, "could not read mod: " + e.getMessage());
        }

        final JsonNode info;
        try {
            info = lenientMapper.readTree(raw.startsWith("\uFEFF") ? raw.substring(1) : raw);
        } catch (RuntimeException e) {
            return new LocalMod(entry, "invalid modinfo.json: " + e.getMessage());
        }

        final String modid = getCi(info, "modid");
        final String version = getCi(info, "version", "modversion");
        if (modid == null || version == null) {
            return new LocalMod(entry, "modinfo.json missing modid/version");
        }
        return new LocalMod(entry, modid, version, getCi(info, "name"), getCi(info, "description"), icon,
                dependenciesOf(info));
    }

    /** Case-insensitive lookup of the first present text field. */
    private static String getCi(final JsonNode node, final String... keys) {
        for (final String key : keys) {
            for (final Map.Entry<String, JsonNode> property : node.properties()) {
                if (property.getKey().equalsIgnoreCase(key) && !property.getValue().isNull()) {
                    return property.getValue().asString();
                }
            }
        }
        return null;
    }

    private static Map<String, String> dependenciesOf(final JsonNode info) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonNode> property : info.properties()) {
            if (property.getKey().equalsIgnoreCase("dependencies") && property.getValue().isObject()) {
                for (final Map.Entry<String, JsonNode> dep : property.getValue().properties()) {
                    if (!BUILT_IN.contains(dep.getKey().toLowerCase(Locale.ROOT))) {
                        result.put(dep.getKey(), dep.getValue().asString(""));
                    }
                }
            }
        }
        return result;
    }

    private static String readZipText(final ZipFile zip, final String name) throws IOException {
        final byte[] bytes = readZipBytes(zip, name);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Reads the shallowest entry whose file name matches, ignoring case. */
    private static byte[] readZipBytes(final ZipFile zip, final String name) throws IOException {
        ZipEntry best = null;
        for (final var entries = zip.entries(); entries.hasMoreElements();) {
            final ZipEntry candidate = entries.nextElement();
            final String path = candidate.getName();
            if (!candidate.isDirectory() && path.substring(path.lastIndexOf('/') + 1).equalsIgnoreCase(name)
                    && (best == null || depth(path) < depth(best.getName()))) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(best)) {
            return in.readAllBytes();
        }
    }

    private static int depth(final String path) {
        return (int) path.chars().filter(c -> c == '/').count();
    }

    private static File findIgnoreCase(final File dir, final String name) {
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.isFile() && file.getName().equalsIgnoreCase(name)) {
                    return file;
                }
            }
        }
        return null;
    }

    private static String readDirText(final File dir, final String name) throws IOException {
        final byte[] bytes = readDirBytes(dir, name);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] readDirBytes(final File dir, final String name) throws IOException {
        final File file = findIgnoreCase(dir, name);
        return file == null ? null : Files.readAllBytes(file.toPath());
    }

    public static Map<String, List<LocalMod>> indexByModid(final List<LocalMod> mods) {
        final Map<String, List<LocalMod>> index = new LinkedHashMap<>();
        for (final LocalMod mod : mods) {
            if (mod.getModid() != null) {
                index.computeIfAbsent(mod.getModid().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(mod);
            }
        }
        return index;
    }

    // ----------------------------------------------------------- update checks

    /** Checks every mod against the mod database in parallel, reporting each result as it arrives. */
    public void checkForUpdates(final List<LocalMod> mods, final Consumer<ModStatus> onResult) {
        final ExecutorService pool = Executors.newFixedThreadPool(MAX_WORKERS);
        try {
            for (final LocalMod mod : mods) {
                pool.submit(() -> onResult.accept(check(mod)));
            }
        } finally {
            pool.shutdown();
        }
        try {
            pool.awaitTermination(10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ModStatus check(final LocalMod mod) {
        if (mod.getError() != null) {
            return new ModStatus(mod, null, mod.getError());
        }
        try {
            final ModRecord record = client.fetch(mod.getModid());
            if (record.latest() == null) {
                return new ModStatus(mod, null, "no releases published");
            }
            return new ModStatus(mod, record.latest(), null);
        } catch (ModDbException e) {
            return new ModStatus(mod, null, e.getMessage());
        }
    }

    /** Dependencies declared by installed mods that nothing in the Mods folder provides. */
    public static List<Request> findMissingDependencies(final List<LocalMod> mods) {
        final Map<String, List<LocalMod>> index = indexByModid(mods);
        final Set<String> seen = new HashSet<>();
        final List<Request> missing = new ArrayList<>();
        for (final LocalMod mod : mods) {
            for (final String dep : mod.getDependencies().keySet()) {
                final String key = dep.toLowerCase(Locale.ROOT);
                if (!index.containsKey(key) && seen.add(key)) {
                    missing.add(new Request(dep, mod.getName()));
                }
            }
        }
        return missing;
    }

    // ------------------------------------------------------ plan and install

    /**
     * Resolves the requested mods and, breadth-first, everything they depend on. Dependencies are read
     * from each new release's modinfo.json (the mod database does not expose them), so changed mods are
     * downloaded here; nothing is written to the Mods folder until {@link #apply}.
     */
    public Plan resolve(final List<Request> requested, final List<LocalMod> localMods) {
        final Map<String, List<LocalMod>> index = indexByModid(localMods);
        final List<PlanEntry> entries = new ArrayList<>();
        final Map<String, String> errors = new LinkedHashMap<>();
        final Set<String> seen = new LinkedHashSet<>();
        final List<Request> queue = new ArrayList<>(requested);

        while (!queue.isEmpty()) {
            final Request request = queue.remove(0);
            final String key = request.modid().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }

            final ModRecord record;
            try {
                record = client.fetch(request.modid());
            } catch (ModDbException e) {
                errors.put(request.modid(), e.getMessage());
                continue;
            }
            final Release release = record.latest();
            if (release == null || release.mainFile() == null) {
                errors.put(request.modid(), "no releases published");
                continue;
            }

            final List<LocalMod> existing = index.getOrDefault(key, List.of());
            final LocalMod current = existing.stream()
                    .filter(m -> release.version() != null && release.version().equals(m.getVersion()))
                    .findFirst().orElse(null);

            final Set<String> dependencies;
            byte[] data = null;
            if (current != null) {
                dependencies = current.getDependencies().keySet();
            } else {
                try {
                    data = client.download(release.mainFile());
                    dependencies = dependenciesInZip(data);
                } catch (IOException e) {
                    errors.put(request.modid(), "download failed: " + e.getMessage());
                    continue;
                }
            }

            entries.add(new PlanEntry(key, record.name() != null ? record.name() : request.modid(),
                    release.version(), release, data, existing, request.requiredBy()));
            for (final String dep : dependencies) {
                queue.add(new Request(dep, record.name() != null ? record.name() : request.modid()));
            }
        }
        return new Plan(entries, errors);
    }

    private Set<String> dependenciesInZip(final byte[] data) throws IOException {
        final Path temp = Files.createTempFile("vsmod", ".zip");
        try {
            Files.write(temp, data);
            try (ZipFile zip = new ZipFile(temp.toFile())) {
                final String raw = readZipText(zip, "modinfo.json");
                if (raw == null) {
                    return Set.of();
                }
                return dependenciesOf(lenientMapper.readTree(raw.startsWith("\uFEFF") ? raw.substring(1) : raw))
                        .keySet();
            }
        } catch (RuntimeException e) {
            return Set.of(); // unreadable modinfo.json: install it anyway, just without dependencies
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Writes each changed entry into the Mods folder and removes the copies it replaces. */
    public List<String> apply(final Plan plan, final File modsDir) {
        final List<String> messages = new ArrayList<>();
        modsDir.mkdirs();
        for (final PlanEntry entry : plan.changes()) {
            // The file name comes from the network: keep only its last path element.
            final String fileName = entry.release().fileName() != null
                    ? Path.of(entry.release().fileName()).getFileName().toString()
                    : entry.modid() + ".zip";
            final File destination = new File(modsDir, fileName);
            try {
                Files.write(destination.toPath(), entry.data());
                for (final LocalMod old : entry.existing()) {
                    final File oldFile = new File(modsDir, old.getSource());
                    if (!oldFile.getCanonicalFile().equals(destination.getCanonicalFile())) {
                        deleteRecursively(oldFile);
                    }
                }
                messages.add((entry.existing().isEmpty() ? "Installed " : "Updated ") + entry.name() + " "
                        + entry.version());
            } catch (IOException | RuntimeException e) {
                messages.add("Failed " + entry.name() + ": " + e.getMessage());
            }
        }
        return messages;
    }

    private static void deleteRecursively(final File file) throws IOException {
        final File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        Files.deleteIfExists(file.toPath());
    }

}
