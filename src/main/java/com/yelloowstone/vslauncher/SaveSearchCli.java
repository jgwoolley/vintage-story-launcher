package com.yelloowstone.vslauncher;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.yelloowstone.vslauncher.SaveGameScanner.SaveInfo;
import com.yelloowstone.vslauncher.SaveGameStructures.Structure;
import com.yelloowstone.vslauncher.SaveGameStructures.Type;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code search}: the map page's save search on the command line, without starting JavaFX. Works on single player
 * saves and on a dedicated server's data folder (same file format), read-only; a save the game or a server has open
 * is read live (see {@link GameDatabase}). Exit code 0 when something matched, 1 when nothing did, 2 on errors.
 */
public final class SaveSearchCli {
    private static final String USAGE = """
            Usage: java -jar vintagestory-launcher.jar search <save> [options]
                   java -jar vintagestory-launcher.jar search --list [<folder>...]
            (the installed app takes the same arguments)

            Lists what a save records: generated structures, story locations, players and their spawn points,
            waypoints, last deaths, prospecting readings, translocators and traders. Read-only; a save the game or a
            server has open is read live.

            <save>   a .vcdbs file; a data folder (single player data folder or a dedicated server's data folder, with
                     Saves/ and Playerdata/); or a world, save file or instance name from the launcher's instances

            Options
              -q, --query <text>     part of a name or detail (shrine, copper, a player), or a pattern like *vugs
              -t, --type <kinds>     comma separated: structure, story, player, spawn, waypoint, death, translocator,
                                     ore, trader (singular, plural or a start like "trans"; default: all)
                  --same <kind:name> rows like the Same ... buttons: player:Yelloow (position, spawn, death),
                                     trader:"agriculture trader", structure:undergroundruin, ore:nativecopper
                  --schematic <file> only structures built from this schematic (s-kitchen1.json)
                  --pair <x z>       the translocator at x z and its other end
                  --near <x z>       distances (and sorting) from this point instead of spawn
                  --radius <blocks>  only within this distance (of --near, else of spawn)
                  --sort <by>        distance (default), name, type, y
                  --limit <n>        at most n rows
                  --format <f>       table (default in a terminal), tsv (default when piped), json, tp (/tp commands)
                  --no-chunks        skip the slow scan of every chunk (no translocators or traders)
                  --backup           search the newest backup (Backups/, BackupSaves/) of the data folder instead
                  --world <name>     which save, when a data folder has several
                  --list             list the saves found (in the given folders, else in every instance)
              -h, --help

            Coordinates: plain numbers are relative to spawn, = makes them absolute (=512100 =511900), like Go to.
            Exit code: 0 something matched, 1 nothing matched, 2 error.

            Examples
              search "My World" -t trader
              search /srv/vintagestory/data -t translocator --format json
              search "My World" -q copper -t ore --near "0 0" --radius 500
              search "My World" --same player:Yelloow
              search "My World" -t story --format tp | head -1
            """;

    private final PrintStream out;
    private final PrintStream err;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    private SaveSearchCli(final PrintStream out, final PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /** Runs {@code search} with the arguments after the word "search"; returns the exit code. */
    public static int run(final String[] args) {
        return new SaveSearchCli(System.out, System.err).main(args);
    }

    /** A wrong argument: the message is shown with a hint to read --help, exit code 2. */
    private static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(final String message) {
            super(message);
        }
    }

    private int main(final String[] args) {
        try {
            return search(args);
        } catch (UsageException e) {
            err.println("search: " + e.getMessage());
            err.println("Try: search --help");
            return 2;
        } catch (RuntimeException e) {
            err.println("search: " + e);
            return 2;
        }
    }

    private static String value(final String[] args, final int i, final String option) {
        if (i >= args.length) {
            throw new UsageException(option + " needs a value");
        }
        return args[i];
    }

    private int search(final String[] args) {
        String target = null;
        String query = null;
        Set<Type> types = EnumSet.allOf(Type.class);
        String same = null, schematic = null, pair = null, near = null, sort = "distance", format = null, world = null;
        Double radius = null;
        Integer limit = null;
        boolean noChunks = false, backup = false, list = false;
        final List<String> folders = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            final String a = args[i];
            switch (a) {
            case "-h", "--help" -> {
                out.print(USAGE);
                return 0;
            }
            case "-q", "--query" -> query = value(args, ++i, a);
            case "-t", "--type" -> types = parseTypes(value(args, ++i, a));
            case "--same" -> same = value(args, ++i, a);
            case "--schematic" -> schematic = value(args, ++i, a);
            case "--pair" -> pair = value(args, ++i, a);
            case "--near" -> near = value(args, ++i, a);
            case "--radius" -> radius = number(value(args, ++i, a), a);
            case "--sort" -> sort = value(args, ++i, a).toLowerCase(Locale.ROOT);
            case "--limit" -> limit = (int) (double) number(value(args, ++i, a), a);
            case "--format" -> format = value(args, ++i, a).toLowerCase(Locale.ROOT);
            case "--world" -> world = value(args, ++i, a);
            case "--no-chunks" -> noChunks = true;
            case "--backup" -> backup = true;
            case "--list" -> list = true;
            default -> {
                if (a.startsWith("-") && a.length() > 1 && !a.matches("-\\d.*")) {
                    throw new UsageException("unknown option " + a);
                }
                if (list) {
                    folders.add(a);
                } else if (target == null) {
                    target = a;
                } else {
                    throw new UsageException("one save at a time (got '" + target + "' and '" + a + "')");
                }
            }
            }
        }
        if (list) {
            if (target != null) {
                folders.add(0, target);
            }
            return list(folders);
        }
        if (target == null) {
            throw new UsageException("which save? Give a .vcdbs file, a data folder or a world name (--list shows them)");
        }
        if (format == null) {
            format = System.console() != null ? "table" : "tsv";
        }
        if (!List.of("table", "tsv", "json", "tp").contains(format)) {
            throw new UsageException("--format is table, tsv, json or tp");
        }
        if (!List.of("distance", "name", "type", "y").contains(sort)) {
            throw new UsageException("--sort is distance, name, type or y");
        }
        if (noChunks) {
            types.removeAll(SaveSearch.CHUNK_TYPES);
        }

        final File[] found = resolve(target, world, backup);
        final File save = found[0];
        final File dataPath = found[1];
        final SaveInfo info = SaveGameScanner.read(save);
        final MapCoordinates coordinates = new MapCoordinates(info != null ? info.origin() : MapCoordinates.DEFAULT_ORIGIN);
        final double[] from = near == null ? new double[] { coordinates.getOriginX(), coordinates.getOriginZ() }
                : parsePoint(coordinates, near, "--near");

        Predicate<Structure> filter = s -> true;
        if (same != null) {
            final int colon = same.indexOf(':');
            if (colon < 1) {
                throw new UsageException("--same is kind:name, e.g. player:Yelloow or trader:\"agriculture trader\"");
            }
            final Type type = SaveSearch.parseType(same.substring(0, colon));
            if (type == null) {
                throw new UsageException("unknown kind '" + same.substring(0, colon) + "' in --same");
            }
            filter = filter.and(SaveSearch.sameAs(type, same.substring(colon + 1).strip()));
            types.add(type);
            if (type == Type.PLAYER || type == Type.SPAWN || type == Type.DEATH) {
                types.addAll(EnumSet.of(Type.PLAYER, Type.SPAWN, Type.DEATH));
            }
        }
        if (pair != null) {
            types.add(Type.TRANSLOCATOR);
        }
        final boolean terminal = System.console() != null;
        if (terminal && types.stream().anyMatch(SaveSearch.CHUNK_TYPES::contains)) {
            err.println("Reading " + save + (GameDatabase.isLive(save) ? " (live)" : "")
                    + "; scanning every chunk for translocators and traders (--no-chunks skips it)...");
        }
        final List<Structure> all = SaveSearch.read(save, dataPath, mapper, types);

        final String text = query;
        filter = filter.and(s -> s.matches(text));
        if (schematic != null) {
            final String file = schematic;
            filter = filter.and(s -> s.schematic().equalsIgnoreCase(file));
        }
        if (pair != null) {
            final double[] p = parsePoint(coordinates, pair, "--pair");
            final Structure translocator = all.stream().filter(s -> s.type() == Type.TRANSLOCATOR)
                    .min(Comparator.comparingDouble(s -> SaveSearch.distance(s, p[0], p[1]))).orElse(null);
            if (translocator == null || SaveSearch.distance(translocator, p[0], p[1]) > 3) {
                throw new UsageException("no translocator at " + coordinates.describe(p[0], p[1]));
            }
            filter = filter.and(s -> SaveSearch.pairOf(translocator, s));
        }
        if (radius != null) {
            final double r = radius;
            filter = filter.and(s -> SaveSearch.distance(s, from[0], from[1]) <= r);
        }
        final Comparator<Structure> byDistance = Comparator.comparingDouble(s -> SaveSearch.distance(s, from[0], from[1]));
        final Comparator<Structure> order = switch (sort) {
        case "name" -> Comparator.comparing((Structure s) -> s.kind().toLowerCase(Locale.ROOT)).thenComparing(byDistance);
        case "type" -> Comparator.comparing(Structure::type).thenComparing(byDistance);
        case "y" -> Comparator.comparingInt(Structure::y1).thenComparing(byDistance);
        default -> byDistance;
        };
        List<Structure> rows = all.stream().filter(filter).sorted(order).toList();
        final int total = rows.size();
        if (limit != null && limit >= 0 && rows.size() > limit) {
            rows = rows.subList(0, limit);
        }

        switch (format) {
        case "json" -> printJson(rows, coordinates, from);
        case "tp" -> rows.forEach(s -> out.println(SaveSearch.tpCommand(s)));
        default -> printTable(rows, coordinates, from, format.equals("table"), terminal);
        }
        if (terminal && !format.equals("json")) {
            err.println(rows.size() + (rows.size() == total ? "" : " of " + total) + " rows"
                    + (info != null ? " in " + info.displayName() : "")
                    + (near == null ? ", distances from spawn" : ", distances from " + coordinates.describe(from[0], from[1])));
        }
        return rows.isEmpty() ? 1 : 0;
    }

    private static Double number(final String text, final String option) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new UsageException(option + " needs a number, not '" + text + "'");
        }
    }

    private static double[] parsePoint(final MapCoordinates coordinates, final String text, final String option) {
        try {
            return coordinates.parse(text);
        } catch (IllegalArgumentException e) {
            throw new UsageException(option + ": " + e.getMessage());
        }
    }

    private static Set<Type> parseTypes(final String text) {
        final Set<Type> types = EnumSet.noneOf(Type.class);
        for (final String word : text.split(",")) {
            if (word.isBlank()) {
                continue;
            }
            final Type type = SaveSearch.parseType(word.strip());
            if (type == null) {
                throw new UsageException("unknown type '" + word.strip() + "' (structure, story, player, spawn, waypoint,"
                        + " death, translocator, ore, trader)");
            }
            types.add(type);
        }
        if (types.isEmpty()) {
            throw new UsageException("--type needs at least one kind");
        }
        return types;
    }

    // ---- finding the save ------------------------------------------------------------------------------------------

    /** The save file and its data folder (for player names) for the {@code <save>} argument. */
    private File[] resolve(final String target, final String world, final boolean backup) {
        final File file = new File(target);
        if (file.isFile()) {
            if (backup) {
                throw new UsageException("--backup needs a data folder, not a save file");
            }
            return new File[] { file, dataPathOf(file) };
        }
        if (file.isDirectory()) {
            final File dataPath = file.getName().equals("Saves") && file.getParentFile() != null ? file.getParentFile() : file;
            return new File[] { pick(saves(dataPath, backup), world, target), dataPath };
        }
        // a name: worlds, save files and instances of the launcher's instances
        final List<File[]> matches = new ArrayList<>();
        for (final Map.Entry<String, File> instance : instances().entrySet()) {
            final List<File> saves = saves(instance.getValue(), backup);
            for (final File save : saves) {
                final SaveInfo info = SaveGameScanner.read(save);
                final boolean instanceMatch = instance.getKey().equalsIgnoreCase(target)
                        && (world == null ? saves.size() == 1 : matchesWorld(save, info, world));
                if (instanceMatch || matchesWorld(save, info, target)) {
                    matches.add(new File[] { save, instance.getValue() });
                }
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new UsageException("no save, world or instance called '" + target + "' (see search --list)");
        }
        throw new UsageException("'" + target + "' matches several saves; give the file:\n  "
                + String.join("\n  ", matches.stream().map(m -> m[0].getPath()).toList()));
    }

    private static boolean matchesWorld(final File save, final SaveInfo info, final String name) {
        final String base = save.getName().replaceFirst("\\.vcdbs$", "");
        return base.equalsIgnoreCase(name) || (info != null && name.equalsIgnoreCase(info.worldName()));
    }

    /** One save from a folder's: the only one, or the one named by --world. */
    private File pick(final List<File> saves, final String world, final String folder) {
        if (saves.isEmpty()) {
            throw new UsageException("no saves (.vcdbs) in " + folder);
        }
        if (world != null) {
            return saves.stream().filter(s -> matchesWorld(s, SaveGameScanner.read(s), world)).findFirst()
                    .orElseThrow(() -> new UsageException("no save called '" + world + "' in " + folder));
        }
        if (saves.size() == 1) {
            return saves.get(0);
        }
        throw new UsageException(folder + " has several saves; pick one with --world <name> or give the file:\n  "
                + String.join("\n  ", saves.stream().map(File::getPath).toList()));
    }

    /** The saves of a data folder (or of a folder of .vcdbs files); with backup, only the newest backup. */
    private static List<File> saves(final File folder, final boolean backup) {
        if (backup) {
            final List<File> backups = new ArrayList<>();
            for (final String name : List.of("Backups", "BackupSaves")) {
                backups.addAll(vcdbs(new File(folder, name)));
            }
            return backups.stream().max(Comparator.comparingLong(File::lastModified)).map(List::of).orElse(List.of());
        }
        final List<File> saves = vcdbs(new File(folder, "Saves"));
        return saves.isEmpty() ? vcdbs(folder) : saves;
    }

    private static List<File> vcdbs(final File folder) {
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".vcdbs"));
        if (files == null) {
            return List.of();
        }
        final List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparing(File::getName));
        return list;
    }

    /** {@code <data>/Saves/x.vcdbs} (or Backups/, BackupSaves/) -> {@code <data>}; otherwise the file's folder. */
    private static File dataPathOf(final File save) {
        final File parent = save.getAbsoluteFile().getParentFile();
        if (parent != null && List.of("Saves", "Backups", "BackupSaves").contains(parent.getName())
                && parent.getParentFile() != null) {
            return parent.getParentFile();
        }
        return parent;
    }

    /** Instance name -> data folder, from the launcher's {@code config.json}. */
    private Map<String, File> instances() {
        final Map<String, File> instances = new LinkedHashMap<>();
        final File config = new File(new File(System.getProperty("user.home"), ".config/VSLauncher"), "config.json");
        if (!config.isFile()) {
            return instances;
        }
        try {
            for (final JsonNode instance : mapper.readTree(config)) {
                final String name = instance.path("name").asString(null);
                final String dataPath = instance.path("dataPath").asString(null);
                if (name != null && dataPath != null) {
                    instances.put(name, new File(dataPath));
                }
            }
        } catch (RuntimeException e) {
            err.println("search: could not read " + config + ": " + e.getMessage());
        }
        return instances;
    }

    private int list(final List<String> folders) {
        final Map<String, File> sources = new LinkedHashMap<>();
        if (folders.isEmpty()) {
            sources.putAll(instances());
        } else {
            folders.forEach(f -> sources.put(f, new File(f)));
        }
        int count = 0;
        final List<String[]> table = new ArrayList<>();
        table.add(new String[] { "SOURCE", "WORLD", "FILE", "SAVE ID", "STATE" });
        for (final Map.Entry<String, File> source : sources.entrySet()) {
            final List<File> saves = new ArrayList<>(saves(source.getValue(), false));
            saves.addAll(saves(source.getValue(), true).stream().filter(b -> !saves.contains(b)).toList());
            for (final File save : saves) {
                final SaveInfo info = SaveGameScanner.read(save);
                final boolean isBackup = save.getParentFile().getName().startsWith("Backup");
                final List<String> state = new ArrayList<>();
                if (isBackup) {
                    state.add("newest backup");
                }
                if (GameDatabase.isLive(save)) {
                    state.add("open (read live)");
                }
                table.add(new String[] { source.getKey(), info == null || info.worldName() == null ? "" : info.worldName(),
                        save.getPath(), info == null ? "(unreadable)" : info.id(), String.join(", ", state) });
                count++;
            }
        }
        if (count == 0) {
            err.println(folders.isEmpty() ? "No saves in the launcher's instances (" + sources.size() + " instances)."
                    : "No saves in " + String.join(", ", folders) + ".");
            return 1;
        }
        printColumns(table, System.console() != null);
        return 0;
    }

    // ---- output ----------------------------------------------------------------------------------------------------

    private void printTable(final List<Structure> rows, final MapCoordinates coordinates, final double[] from,
            final boolean aligned, final boolean terminal) {
        final List<String[]> table = new ArrayList<>();
        table.add(new String[] { "TYPE", "NAME", "DETAILS", "POSITION", "DISTANCE", "TP" });
        for (final Structure s : rows) {
            table.add(new String[] { s.type().label(), s.kind(), details(s), position(s, coordinates),
                    String.valueOf(Math.round(SaveSearch.distance(s, from[0], from[1]))), SaveSearch.tpCommand(s) });
        }
        if (aligned) {
            printColumns(table, terminal);
        } else {
            table.forEach(row -> out.println(String.join("\t", Arrays.stream(row).map(v -> v.replace('\t', ' ')).toList())));
        }
    }

    /** Aligned columns; in a terminal the widest text column (details) is shortened to fit. */
    private void printColumns(final List<String[]> table, final boolean terminal) {
        final int columns = table.get(0).length;
        final int[] widths = new int[columns];
        for (final String[] row : table) {
            for (int c = 0; c < columns; c++) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }
        final int screen = terminal ? terminalWidth() : Integer.MAX_VALUE;
        int total = Arrays.stream(widths).sum() + 2 * (columns - 1);
        if (total > screen) {
            // shorten the widest column (details / file) down to what fits, but keep at least 20 characters
            int widest = 0;
            for (int c = 1; c < columns; c++) {
                if (widths[c] > widths[widest]) {
                    widest = c;
                }
            }
            widths[widest] = Math.max(20, widths[widest] - (total - screen));
        }
        for (final String[] row : table) {
            final StringBuilder line = new StringBuilder();
            for (int c = 0; c < columns; c++) {
                String cell = row[c];
                if (cell.length() > widths[c]) {
                    cell = cell.substring(0, widths[c] - 1) + "…";
                }
                line.append(c == columns - 1 ? cell : String.format(Locale.ROOT, "%-" + widths[c] + "s  ", cell));
            }
            out.println(line.toString().stripTrailing());
        }
    }

    private static int terminalWidth() {
        try {
            final int columns = Integer.parseInt(System.getenv().getOrDefault("COLUMNS", ""));
            return columns > 40 ? columns : 160;
        } catch (NumberFormatException e) {
            return 160;
        }
    }

    private static String details(final Structure s) {
        return s.schematic().isEmpty() ? s.detail() : s.schematic();
    }

    /** Position relative to spawn, x, y, z, as the map page shows it. */
    private static String position(final Structure s, final MapCoordinates coordinates) {
        return String.format(Locale.ROOT, "%d, %d, %d", Math.round(s.centerX() - coordinates.getOriginX()), s.y1(),
                Math.round(s.centerZ() - coordinates.getOriginZ()));
    }

    private void printJson(final List<Structure> rows, final MapCoordinates coordinates, final double[] from) {
        final List<Map<String, Object>> json = new ArrayList<>();
        for (final Structure s : rows) {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", s.type().name().toLowerCase(Locale.ROOT));
            row.put("name", s.kind());
            row.put("code", s.code());
            row.put("schematic", s.schematic().isEmpty() ? null : s.schematic());
            row.put("detail", s.detail().isEmpty() ? null : s.detail());
            row.put("x", Math.round(s.centerX()));
            row.put("y", s.y1());
            row.put("z", Math.round(s.centerZ()));
            row.put("fromSpawn", List.of(Math.round(s.centerX() - coordinates.getOriginX()), s.y1(),
                    Math.round(s.centerZ() - coordinates.getOriginZ())));
            if (s.y1() != s.y2() || s.x1() != s.x2() || s.z1() != s.z2()) {
                row.put("box", List.of(s.x1(), s.y1(), s.z1(), s.x2(), s.y2(), s.z2()));
            }
            if (s.link() != null) {
                row.put("link", List.of(s.link()[0], s.link()[1], s.link()[2]));
            }
            if (s.color() != 0) {
                row.put("color", String.format(Locale.ROOT, "#%06x", s.color() & 0xffffff));
            }
            row.put("distance", Math.round(SaveSearch.distance(s, from[0], from[1])));
            row.put("tp", SaveSearch.tpCommand(s));
            json.add(row);
        }
        out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
    }
}
