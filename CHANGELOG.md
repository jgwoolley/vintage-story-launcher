# Changelog

All notable changes since 1.0.0. Versions follow `pom.xml`.

## 1.2.1

### Fixes
- Fixed: the launcher crashed on start (an empty window) when an instance's data folder had no `clientsettings.json`, for
  example a server's folder added as a normal instance. Such an instance now shows no player, and one whose folder has a
  `serverconfig.json` is treated as a server instance.
- One broken instance no longer takes the launcher down: an unreadable entry in `config.json` is skipped (the file is
  kept as `config.json.bak`) instead of losing every instance, values that fail to load show blank, missing paths no
  longer break the info page, and any other error is shown in a dialog while the launcher keeps running.

## 1.2.0

### Servers
- **Server instances.** **Add Server** adds a dedicated server's data folder by itself: its name, port and world are
  read from `serverconfig.json` (a `SaveFileLocation` from another machine or a container is looked up in the folder's
  `Saves/`). Server instances are never launched and are left out of Load Last Instance, the tray and copying.
- **Server maps.** **Open Maps** on a server shows the full map page for its world: structures, schematics, players,
  waypoints, translocators, traders... from the server's save, marked over a client instance's map of the same world
  (found by the world ID in the map's file name; **Client Map** picks which, remembered per server, or **None** for
  marks only). Spawn and player names come from the server, so relative coordinates are right.
- A client map whose world belongs to a server instance shows that server as its source.
- Picking a folder with **Add** on a create page now selects it.

### Maps
- **Structures.** A single player map's page lists the structures world generation has placed in the save (ruins,
  traders, vugs, lakes, and modded ones such as Reliquiarum's shrines): search by part of the code or a pattern like
  `*vugs`, sort by distance from spawn, click a row to jump there on the map (with the in-game `/tp` command), and see
  them marked on the map (hover a mark for its name; **Show on map** turns the marks off). Read from the save's map
  regions, so only explored land has them; server maps keep this on the server. The table shows 50 structures a
  page (**Previous** / **Next**; sorting covers every page), and a row's Actions filter to
  **Same Structure** or **Same Schematic** (combinable) until **Show All**.
- **Players on the map.** The structures table also lists every player in a single player save at their last saved
  position (named from `Playerdata/playerdata.json` or `clientsettings.json`), marked on the map as dots in a colour per player (shown
  next to their name in the table, drawn above the jump marker); search
  `player` to list only them.
- **More in the table.** Story locations, players' spawn points, waypoints, last deaths, prospecting readings,
  translocators (a dashed line joins a working one to where it leads; broken ones are grey) and traders (with their
  name) are listed and marked too, each kind with its own mark, shown by the row in a new Type column. A checkbox per
  kind (with counts, all on by default) replaces the Structures / Players toggles. The row button follows the kind:
  **Same Player** lists a player's position, spawn point and death together; **Same Trader**, **Same Ore**, **Same
  Title**. Translocators and traders come from a scan of every world chunk, which runs after the rest has loaded.
- **Copy /tp** in each row's Actions copies the in-game teleport command (absolute coordinates, one block above a
  point or on top of a structure) to the clipboard.
- Clicking a mark on the map selects its row in the table, turning to the right page and scrolling to it; the map stays
  where it is (the red marker moves to the mark). Where marks overlap, the one drawn on top is picked.
- **Translocator links.** Selecting a working translocator frames both ends on the map, draws its link bright with a
  ring on the far end, and the status line says where it leads and how far. Its row's **Show Pair** button lists only
  that translocator and its other end.
- **Command line save search.** `java -jar vintagestory-launcher-<version>.jar search <save> ...` runs the map page's
  search without the window: on a save file, a data folder (also a dedicated server's) or a world / instance name,
  with text, kind, same-as, schematic, translocator pair and distance filters, table / TSV / JSON / `/tp` output and
  `--backup` to search the newest backup. `search --help` for the details.
- Saves and maps the game or a server has open are now read live (plain read-only, which includes changes not yet
  merged from the `-wal` file); closed ones are still opened `immutable`, which leaves no files behind.
- Fixed: skipping a length-delimited protobuf field landed one byte short (only reached by the new readers).

### Instances
- **Instances are editable.** On the info page click a value to edit it in line: Name, Runtime Path, Data Path
  (both must be existing folders; nothing is moved), plus new **Launch Arguments**, **Wrapper Command** (for
  example `gamemoderun`) and **Environment** (`KEY=value` words). Wrapper and environment do not apply on macOS.
  Calculated rows (Player Name, Version, Last Open, Mod Paths) are fixed.
- The instance info page has an Actions column like the instance list: **Open** (folder) on the Runtime Path and Data Path
  rows and **Edit** on the Mod Paths row. Open, Manage Mods, Open Maps and Back stay on their own lines.
- **Merge Nf3t Data** on the Copy Instance Data page merges the source's `ModData/Nf3tData.json` (translocators,
  waypoints, spawn positions of the Nf3t mods) into the destination's, using the mods' own rules: the destination
  wins on conflicts, unknown translocator targets are filled in, and the destination file is backed up first.
- **Multiple mod paths** per instance. **Edit Mod Paths** shows the paths from `clientsettings.json` (read-only,
  the game owns them) and the launcher's own paths (edit, add, remove, move to top). Each launcher path is passed
  with one `--addModPath` option (the game crashes when it is repeated; several folders after one option work on 1.22.7) unless the game already lists it; the first one is where the Mods page installs mods.
  Existing single `modPath` entries in `config.json` are migrated automatically.
- **Duplicate mod warning.** The Mods page now checks every folder the game loads (launcher mod paths, the
  game's `clientsettings.json` paths, the runtime's `Mods`). A mod id found in more than one folder gets a
  warning in the new Notice column and a banner: the game loads only the highest version and ignores the rest.
- The Merge Maps page explains that only copies of the same world (same map file name) can be merged, and says
  so when no other instance has one.

## 1.1.0

### Mods
- **Manage Mods** page for every instance: lists the mods in the instance's Mod Path with their icon,
  description, installed version and the latest version on [mods.vintagestory.at](https://mods.vintagestory.at).
- **Update a single mod or all of them.** Updates also install the dependencies a new release needs and
  update outdated ones; a confirmation dialog lists every change before anything is written. Built-in
  dependencies (`game`, `survival`, `creative`, `essentials`) are ignored.
- Installed mods missing a dependency are detected and the dependency is installed by **Update All**.
- Tolerates hand-written `modinfo.json` files (comments, trailing commas, single quotes).
- **Servers:** a dropdown also shows the mods the game downloaded into `ModsByServer/<host-port>`, together
  with the instance's own mods and any other mod paths from `clientsettings.json`. These views are read-only
  and each row shows its location (Server / Instance / Mod path).
- **Export Manifest** writes the mods you have, pinned to their installed versions, as `vs-manifest.json`
  with the game version of the instance, plus `vs-manifest.schema.json` next to it.

### Instances
- **Mod Path** is configured per instance, like Runtime Path and Instance Path (create page, info page,
  and an "Open Mod Path" button on the Mods page). Existing instances keep using `<data path>/Mods`.
- The instance's Mods folder is passed to the game with `--addModPath` even when `clientsettings.json`
  points elsewhere.
- **Load Last Instance** opens the most recently opened instance.
- Opening an instance no longer freezes the launcher ("app is not responding" on Linux): the game runs on a
  background thread and its output is no longer left unread.

### Maps
- Map sizes are shown in a readable form (`3.4 MiB`) and still sort by size.
- **Name your maps**, and see where each came from: a single player save (read from `Saves/*.vcdbs`) or a
  server (recorded when you play: the launcher notes which map changed and which server the log names).
- **Map preview** drawn from the map file, with **pan** (drag), **zoom** (scroll, down to single blocks)
  and **Go to** a coordinate. Coordinates work as in the game: plain numbers are relative to spawn and
  numbers starting with `=` are absolute (`100 -250`, `=512100 =511900`). A y value is accepted and
  ignored, so pasted `x y z` coordinates work. A crosshair marks the spot and hovering shows the
  coordinates under the cursor.
- Fixed map reading: the old code decoded tiles incorrectly and truncated positions. Maps are now opened
  read-only so no `-wal`/`-shm` files appear in the game's `Maps` folder.

### Settings
- **Time zone**, **relative times** ("5 min ago" or the date) and **scroll speed** are configurable and saved
  in `settings.json`.
- Times (last opened, build time) are shown in the chosen zone and still sort correctly.
- The build time is stored in UTC and shown in local time.

### Interface
- **System tray icon** whose menu opens the 5 most recently opened instances (with when each was last
  opened), shows the launcher, or exits. Clicking the icon shows the launcher. A new setting keeps the
  launcher running in the tray when its window is closed. Shown only where the desktop provides a tray
  (some Linux desktops, such as GNOME, need an extension for tray icons).
- The launcher window now has the application icon.
- Buttons have icons.
- **Exit** closes immediately; it no longer waits so a click sound can play.
- Display name is now "Nf3t’s VintageStory Launcher".

### Packaging and build
- `build.sh` builds a `.deb` (version taken from `pom.xml`, application icon, display name, separate
  package name `vintagestory-launcher`), stops on the first error, checks for Maven, `fakeroot` and the JDK
  `jmods`, and prints the install and reinstall commands.
- The `.deb` now includes the application icon and the correct version.
