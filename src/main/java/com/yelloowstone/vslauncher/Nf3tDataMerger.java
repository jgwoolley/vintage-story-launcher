package com.yelloowstone.vslauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Merges one {@code ModData/Nf3tData.json} (written by the Nf3t mods, see {@code SaveData.cs} in the mod repo) into
 * another. Mirrors the mod's own merge: the destination wins on conflicts, and the source only adds what is missing.
 * <ul>
 * <li>{@code TranslocatorsPerSavegame}: union by position; an unknown target ({@code null}) is filled in from the
 * source, a known target is never replaced.</li>
 * <li>{@code DefaultSpawnPositionPerSavegame}, {@code LastTranslocatorPathPerSavegame}: added for savegames the
 * destination does not have.</li>
 * <li>{@code WayPointsPerSavegame}: union by position (the mod treats two waypoints at one spot as duplicates).</li>
 * </ul>
 * Works on the raw JSON tree so fields this launcher does not know about survive.
 */
public final class Nf3tDataMerger {
    public static final String RELATIVE_PATH = "ModData/Nf3tData.json";

    private Nf3tDataMerger() {
    }

    /** What a merge added to the destination. */
    public record Result(int translocators, int filledTargets, int spawnPositions, int paths, int waypoints, File backup) {
        public boolean changedAnything() {
            return translocators + filledTargets + spawnPositions + paths + waypoints > 0;
        }

        public String describe() {
            if (!changedAnything()) {
                return "Nothing to merge: the destination already has everything from the source.";
            }
            return "Merged: " + translocators + " translocator(s) added, " + filledTargets + " destination(s) filled in, "
                    + waypoints + " waypoint(s) added, " + spawnPositions + " spawn position(s), " + paths
                    + " last path(s)." + (backup == null ? "" : " Backup: " + backup.getName());
        }
    }

    public static File fileOf(final VintageStoryInstance instance) {
        return new File(instance.getDataPath(), RELATIVE_PATH);
    }

    /** Merges the source instance's file into the destination's. Writes only when something changes. */
    public static Result merge(final File source, final File destination, final ObjectMapper mapper) throws IOException {
        if (!source.isFile()) {
            throw new IOException("The source has no " + RELATIVE_PATH + " yet.");
        }
        final JsonNode src = mapper.readTree(source);
        if (!src.isObject()) {
            throw new IOException(source + " is not a JSON object.");
        }
        final ObjectNode dst = destination.isFile() ? asObject(mapper.readTree(destination), destination) : mapper.createObjectNode();

        final int[] counts = merge((ObjectNode) src, dst);
        File backup = null;
        if (counts[0] + counts[1] + counts[2] + counts[3] + counts[4] > 0) {
            if (destination.isFile()) {
                backup = new File(destination.getParentFile(), destination.getName() + ".bak-"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                Files.copy(destination.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (destination.getParentFile() != null) {
                destination.getParentFile().mkdirs();
            }
            // Write then move, like the mod, so a crash cannot leave a half-written file.
            final File temp = new File(destination.getParentFile(), destination.getName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp, dst);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return new Result(counts[0], counts[1], counts[2], counts[3], counts[4], backup);
    }

    private static ObjectNode asObject(final JsonNode node, final File file) throws IOException {
        if (!node.isObject()) {
            throw new IOException(file + " is not a JSON object.");
        }
        return (ObjectNode) node;
    }

    /** Merges {@code src} into {@code dst}; returns counts: translocators, filled targets, spawns, paths, waypoints. */
    static int[] merge(final ObjectNode src, final ObjectNode dst) {
        int translocators = 0;
        int filled = 0;
        int spawns = 0;
        int paths = 0;
        int waypoints = 0;

        final JsonNode srcTranslocators = src.get("TranslocatorsPerSavegame");
        if (srcTranslocators != null && srcTranslocators.isObject()) {
            final ObjectNode dstTranslocators = objectField(dst, "TranslocatorsPerSavegame");
            for (final Map.Entry<String, JsonNode> world : srcTranslocators.properties()) {
                final ArrayNode target = arrayField(dstTranslocators, world.getKey());
                for (final JsonNode entry : world.getValue()) {
                    final JsonNode existing = findByKey(target, "Position", entry.get("Position"));
                    if (existing == null) {
                        target.add(entry.deepCopy());
                        translocators++;
                    } else if (isNullOrMissing(existing.get("TargetLocation")) && !isNullOrMissing(entry.get("TargetLocation"))) {
                        // Never downgrade a known destination to "unknown".
                        ((ObjectNode) existing).set("TargetLocation", entry.get("TargetLocation").deepCopy());
                        filled++;
                    }
                }
            }
        }

        spawns += addMissingWorlds(src, dst, "DefaultSpawnPositionPerSavegame");
        paths += addMissingWorlds(src, dst, "LastTranslocatorPathPerSavegame");

        final JsonNode srcWaypoints = src.get("WayPointsPerSavegame");
        if (srcWaypoints != null && srcWaypoints.isObject()) {
            final ObjectNode dstWaypoints = objectField(dst, "WayPointsPerSavegame");
            for (final Map.Entry<String, JsonNode> world : srcWaypoints.properties()) {
                final ArrayNode target = arrayField(dstWaypoints, world.getKey());
                final Set<String> known = new HashSet<>();
                for (final JsonNode waypoint : target) {
                    known.add(posKey(waypoint.get("Pos")));
                }
                for (final JsonNode waypoint : world.getValue()) {
                    if (known.add(posKey(waypoint.get("Pos")))) {
                        target.add(waypoint.deepCopy());
                        waypoints++;
                    }
                }
            }
        }
        return new int[] { translocators, filled, spawns, paths, waypoints };
    }

    private static int addMissingWorlds(final ObjectNode src, final ObjectNode dst, final String field) {
        final JsonNode from = src.get(field);
        if (from == null || !from.isObject()) {
            return 0;
        }
        final ObjectNode to = objectField(dst, field);
        int added = 0;
        for (final Map.Entry<String, JsonNode> world : from.properties()) {
            if (!to.has(world.getKey())) {
                to.set(world.getKey(), world.getValue().deepCopy());
                added++;
            }
        }
        return added;
    }

    private static ObjectNode objectField(final ObjectNode parent, final String name) {
        final JsonNode existing = parent.get(name);
        if (existing != null && existing.isObject()) {
            return (ObjectNode) existing;
        }
        return parent.putObject(name);
    }

    private static ArrayNode arrayField(final ObjectNode parent, final String name) {
        final JsonNode existing = parent.get(name);
        if (existing != null && existing.isArray()) {
            return (ArrayNode) existing;
        }
        return parent.putArray(name);
    }

    private static boolean isNullOrMissing(final JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private static JsonNode findByKey(final ArrayNode array, final String field, final JsonNode key) {
        final String wanted = posKey(key);
        for (final JsonNode item : array) {
            if (posKey(item.get(field)).equals(wanted)) {
                return item;
            }
        }
        return null;
    }

    /** "x,y,z" of a {@code {"X":..,"Y":..,"Z":..}} node, so equal positions compare equal. */
    private static String posKey(final JsonNode pos) {
        if (isNullOrMissing(pos)) {
            return "";
        }
        return pos.path("X").asInt() + "," + pos.path("Y").asInt() + "," + pos.path("Z").asInt();
    }
}
