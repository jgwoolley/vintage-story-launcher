package com.yelloowstone.vslauncher;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.yelloowstone.vslauncher.SaveGameStructures.Structure;
import com.yelloowstone.vslauncher.SaveGameStructures.Type;

import tools.jackson.databind.ObjectMapper;

/**
 * Search rules shared by the map page and the command line ({@link SaveSearchCli}): what counts as "the same" row, a
 * translocator's pair, the {@code /tp} command, and reading only the tables a search needs. No JavaFX here.
 */
public final class SaveSearch {
    private SaveSearch() {
    }

    /** Kinds read from the world data ({@code gamedata}). */
    private static final Set<Type> WORLD_DATA = EnumSet.of(Type.STORY, Type.WAYPOINT, Type.DEATH, Type.ORE);
    /** Kinds read from the chunks (slow). */
    public static final Set<Type> CHUNK_TYPES = EnumSet.of(Type.TRANSLOCATOR, Type.TRADER);

    /**
     * Everything of the given kinds in a save, reading only the tables they need. Player names come from
     * {@code dataPath} (the save's data folder: {@code Playerdata/playerdata.json}, {@code clientsettings.json}).
     */
    public static List<Structure> read(final File save, final File dataPath, final ObjectMapper mapper,
            final Set<Type> types) {
        final List<Structure> rows = new ArrayList<>();
        final Map<String, String> names = SaveGameStructures.playerNames(mapper, dataPath);
        if (types.contains(Type.STRUCTURE)) {
            rows.addAll(SaveGameStructures.read(save));
        }
        if (types.stream().anyMatch(WORLD_DATA::contains)) {
            rows.addAll(SaveGameStructures.readWorldData(save, names));
        }
        if (types.contains(Type.PLAYER) || types.contains(Type.SPAWN)) {
            rows.addAll(SaveGameStructures.readPlayers(save, names));
        }
        if (types.stream().anyMatch(CHUNK_TYPES::contains)) {
            rows.addAll(SaveGameStructures.readChunks(save));
        }
        rows.removeIf(s -> !types.contains(s.type()));
        return rows;
    }

    /**
     * Rows "the same" as one of this kind and name: for {@link Type#PLAYER} any row of that player (position, spawn
     * point, death), otherwise the same type and name. Names compare ignoring case.
     */
    public static Predicate<Structure> sameAs(final Type type, final String name) {
        if (type == Type.PLAYER || type == Type.SPAWN || type == Type.DEATH) {
            return s -> s.ofPlayer() && s.kind().equalsIgnoreCase(name);
        }
        return s -> s.type() == type && s.kind().equalsIgnoreCase(name);
    }

    /** Whether {@code s} is "the same" as the row a Same button was pressed on. */
    public static boolean same(final Structure pressed, final Structure s) {
        return sameAs(pressed.type(), pressed.kind()).test(s);
    }

    /** Whether {@code s} is translocator {@code t} or the translocator at either end of its link. */
    public static boolean pairOf(final Structure t, final Structure s) {
        if (s == t || s.equals(t)) {
            return true;
        }
        if (s.type() != Type.TRANSLOCATOR) {
            return false;
        }
        return at(t.link(), s) || at(s.link(), t);
    }

    /** Whether a block position is where translocator {@code s} stands. */
    private static boolean at(final int[] block, final Structure s) {
        return block != null && block[0] == s.x1() && block[1] == s.y1() && block[2] == s.z1();
    }

    /**
     * The in-game command to teleport to a row, in absolute coordinates: one block above a point, on top of a
     * structure's box.
     */
    public static String tpCommand(final Structure s) {
        return "/tp =" + Math.round(s.centerX()) + " " + (s.y1() == s.y2() ? s.y1() + 1 : s.y2() + 1) + " ="
                + Math.round(s.centerZ());
    }

    /** Horizontal distance from a row's centre to a point. */
    public static double distance(final Structure s, final double x, final double z) {
        return Math.hypot(s.centerX() - x, s.centerZ() - z);
    }

    /**
     * A type from a word: the enum name or its label, singular or plural, or a start of at least 3 letters, ignoring
     * case, spaces and dashes ({@code trader}, {@code Traders}, {@code spawn-points}, {@code ore}, {@code trans}). Null
     * if none matches.
     */
    public static Type parseType(final String word) {
        final String w = normalize(word);
        for (final Type type : Type.values()) {
            for (final String name : List.of(type.name(), type.label(), type.plural())) {
                final String n = normalize(name);
                if (w.equals(n) || (w.length() >= 3 && n.startsWith(w))) {
                    return type;
                }
            }
        }
        return null;
    }

    private static String normalize(final String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }
}
