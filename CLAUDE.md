# CLAUDE.md

JavaFX desktop launcher for [Vintage Story](https://www.vintagestory.at): manages instances (runtime + data +
mod path), mods, and client-side maps. Java 17 target, Maven, no tests yet. Read `README.md` for user-facing
behaviour and `CHANGELOG.md` for history; `RESEARCH.md` holds findings and ideas that are not implemented yet (e.g. dock right-click actions vs. the tray); keep both updated with every user-visible change.

## Build / run
- `mvn compile` to check changes, `mvn clean verify` for the fat JAR (`target/vintagestory-launcher-<version>.jar`, shaded).
- `./build.sh` builds the Linux `.deb` with `jpackage`. Needs `mvn`, `fakeroot` and the JDK `jmods`
  (`openjdk-<n>-jmods`); the script checks for them. Version comes from `pom.xml`.
- `mvn javafx:run` is not configured; run the JAR (`java -jar target/...jar`) or the installed app.
- CI (`.github/workflows/build.yml`) builds installers for Windows/Linux/macOS on `v*` tags.
- This dev container is headless. Test non-GUI logic with small throwaway `main` classes against `target/classes`
  (get the classpath with `mvn dependency:build-classpath`). GUI pieces can run under `xvfb-run -a java
  -Dprism.order=sw -cp <out>:target/classes:<classpath> Main` (GTK 3 is installed; put JavaFX on the classpath, not
  the module path, and start through a non-`Application` `Main`, like `Launcher`); snapshot the scene to a PNG to look
  at it. Audio clips fail there (no sound device), harmlessly. Say so when a GUI change is unverified.

## Layout (`src/main/java/com/yelloowstone/vslauncher`)
The Java package is `com.yelloowstone.vslauncher` (typo'd "yelloowstone"); **never rename it**. The brand is
"Nf3t" everywhere else (README, display name, vendor). Maven groupId is still `com.yelloowstone.nf2t`.

- `App`, `Launcher` – JavaFX entry (`Launcher` is the jar's main class so the shaded JAR starts without module issues;
  `search ...` arguments run the CLI instead).
- `VintageStoryInstance` – `server` instances are only name + dataPath (a dedicated server's folder; `serverMapPath` is
  the client map last shown) and are never launched; everything else comes from `ServerConfig` (`serverconfig.json`:
  name, port, `SaveFileLocation`, looked up in `<dataPath>/Saves` when it is another machine's path). Use
  `Context.getClientInstances()` wherever only game clients make sense. Client instances: mutable: name + runtimePath + dataPath + `modPaths` (launcher-managed list; empty = `<dataPath>/Mods`;
  old single `modPath` is migrated) + launchArgs/wrapper/environment. After editing call `Context.instanceChanged` (saves).
  Serialized to `~/.config/VSLauncher/config.json` by Jackson. `open()` runs the game on a daemon thread
  (never block the FX thread) and records which map belongs to which server when the game exits.
- `VintageStoryMap` – a `Maps/<savegame id>.db` SQLite file: reading tiles, overview rendering.
- `SaveGameScanner` – reads `Saves/*.vcdbs` to map savegame id -> single player save (+ spawn / world size).
- `SaveGameStructures` – everything the map page lists from a save, as `Structure` rows with a `Type`: worldgen structures
  (`mapregion`), players and spawn points (`playerdata`), story locations / waypoints / deaths / ore readings (`gamedata`
  mod data), translocators and traders (`chunk`, slow: a separate background task). `NetBinaryReader` reads the
  .NET `BinaryWriter` / `TreeAttribute` bytes inside chunks.
- `SaveSearch` – GUI-free search rules shared by `MapInfoPage` and the CLI (same-as, translocator pairs, `/tp`, reading
  only the tables a search needs). `SaveSearchCli` – `search` subcommand; `Launcher.main` runs it before JavaFX starts
  when the first argument is `search`. `GameDatabase` – opens the game's SQLite files (see gotchas).
- `MapRegistry` – `maps.json`: user-given map names and the server each map was played on; session tracking.
- `MapCoordinates` – parses "x z" input (plain = relative to spawn, `=` prefix = absolute, optional ignored y).
- `Settings` – `settings.json` (time zone, relative times, scroll speed, close to tray).
- `Protobuf` – tiny wire-format parser (see gotchas). `ProtobufUtils` + `vintagestory.desc/.proto` are older
  and partly wrong; prefer `Protobuf`.
- `mods/` – `ModUpdater` (scan Mods folder, check updates, resolve dependencies, apply), `ModDbClient`
  (mods.vintagestory.at API), `LocalMod`, `ModManifest` (vs-manifest.json export).
- `gui/` – one class per page (`InstanceSelectionPage`, `InstanceInfoPage`, `ModsPage`, `MapsInfoPage`,
  `MapInfoPage`, `ServerMapPage`, `SettingsPage`, ...) building into `context.getRootNode()`; `Context` holds shared state.
  Helpers: `Icons.button(text)` (use instead of `new Button`), `HumanTime` (all date rendering),
  `MapViewer` (pan/zoom canvas; map swappable or null = marks only), `TrayMenu` (system tray).
  `MapInfoPage.createMapSection` is the map + go-to + save table shared by a client map page and `ServerMapPage`
  (a server's save over a client's `Maps/<world id>.db`).

## Conventions
- Match the surrounding code: gui pages are static `create(Context[, ...])` methods that clear and refill
  the root node; play `context.getVolumeProperty().play()` in button handlers.
- One instance must never break the launcher: wrap per-instance reads in GUI code in `Errors.safe(...)` (logs, returns
  a fallback), allow null `runtimePath`/`dataPath`, and remember that an exception escaping `App.start` exits the app.
  `Errors.install` catches the rest on the FX thread (dialog, back to the instance list if the page was left empty).
- New buttons: add an entry for the label in `Icons.BY_TEXT` (icons are Material SVG paths sized from the
  path's own bounds; don't stretch them into a fixed square).
- Dates: keep `LocalDateTime` values in table columns (so sorting is chronological) and format only in the
  cell via `HumanTime.cellFactory()`; sizes likewise stay numeric (`MapsInfoPage.formatSize`).
- Long or blocking work (network, big SQLite maps, mod downloads) goes in a background `Task`/thread and
  updates the UI via `Platform.runLater`/`setOnSucceeded`.
- Config lives in `~/.config/VSLauncher/` (`config.json`, `maps.json`, `settings.json`).
- JSON uses Jackson **3** (`tools.jackson.*`, with `com.fasterxml.jackson.annotation` annotations): `asString()`
  not `asText()`, `JsonNode.properties()`, unchecked exceptions.
- Resources are copied selectively in `pom.xml` (`templates`, `audio`, `css`, `schema`, and `icons/icon-*.png`);
  a new resource directory must be added there.
- Display names must stay shell-safe: `jpackage` puts the app name unquoted into maintainer scripts
  (a straight `'` broke installs; the name uses a typographic `’`, and `build.sh` sets `LC_ALL=C.UTF-8`).
- `vs-manifest.schema.json` (copied into `src/main/resources/schema/`) comes from the sibling
  `vintage-story-wiki` project; keep them in sync.

## Vintage Story data gotchas (learned from real files)
- Map tile `position` is a 64-bit `(tileZ << 27) | tileX`; tiles are 32x32 pixels, 1 pixel = 1 block;
  tile 16000 is the middle of a default (1,024,000 block) world. Read it with `getLong`, not `getInt`.
- Tile `data` is a protobuf with **repeated non-packed int32 pixels (field 1)**, `version` (2),
  `lastModified` (3). Pixels are **ABGR**, not ARGB. The bundled `.desc` wrongly says `bytes pixels`.
- Map file name = the world's `SavegameIdentifier` (GUID); nothing inside the map or in
  `clientsettings.json` links it to a server. Server maps are tied to servers only by watching a play session
  (`client-main.log` has `Connecting to <host>...` and `ModsByServer/<host-port>`). Logs hold the latest
  session only.
- Single player save lookup assumes a `gamedata` table whose blob holds `SaveGame` (id = field 34, world
  name = 13, map size = 1/3, default spawn = 40); this was **not verified against a real save**.
- Generated structures: each `mapregion` row is a protobuf `ServerMapRegion`; field 11 is the repeated
  `GeneratedStructure` with code 1 (`s-kitchen1.json/undergroundruin`), group 2 (often absent) and location 3 (`Cuboidi`,
  x1 y1 z1 x2 y2 z2 = fields 1-6). protobuf-net numbers that class alphabetically, not in declaration order. Region
  blobs also contain **groups** (wire types 3/4), which `Protobuf` skips. Verified against a 1.22.7 server save: positions
  match the game's own `GeneratedStructures`.
- Players: the save's `playerdata` table (`playeruid`, `data`) holds a protobuf `ServerWorldPlayerData`; field 3 is the
  player entity written by .NET `BinaryWriter` (class name, entity, then `EntityPos.ToBytes`), so the position is its
  **last 64 bytes** (x, y, z little-endian doubles first; y = y + dimension * 32768). Names are not in the save: use
  `<dataPath>/Playerdata/playerdata.json` (`PlayerUID` -> `LastKnownPlayername`). Taken from the 1.22.7 decompiled
  code, **not verified against a real save with a player** (only a synthetic blob). Field 14 is `PlayerSpawnPos`
  (x 1, nullable y 2, z 3), present only when the player set their own spawn.
- World data: `gamedata`'s `SaveGame` field 11 is the mod data map (key 1 string, value 2 bytes); values are
  protobuf-net (`SerializerUtil`; a top-level list or dictionary is repeated field 1, dictionary entries key 1 / value 2;
  `double`s are wire type 1, so `Vec3d` = fields 1-3 as fixed64). Read: `storystructurelocations` (code -> location:
  code 1, `Cuboidi` 4; **verified** on a 1.22.7 save), `playerMapMarkers_v2` (`Waypoint`: colour 1, icon 2, owner UID 4,
  `Vec3d` 6, text 9, title 10), `lastDeathLocations` (UID -> `Vec3d`), `oreMapMarkers-<UID>` (`PropickReading`: `Vec3d` 1,
  ore code -> `OreReading` 2 with total factor 2 / parts per thousand 3). The last three are from the 1.22.7 code and
  were empty in every sample save: **unverified**.
- Chunks: `chunk` rows are protobuf `ServerChunk`; field 6 = entities, 8 = block entities, each .NET `BinaryWriter`
  bytes (`NetBinaryReader`). Entity: class name, game version, id (long), watched attributes (`TreeAttribute`), then
  `EntityPos` (x, y, z doubles...), 3 doubles, code. Block entity: class name, then its `TreeAttribute` (`posx/y/z`,
  `blockCode`, ...). Traders (`EntityTrader`, name in `nametag/name`) **verified** on a 1.22.7 save; translocators
  (`StaticTranslocator`: `teleX/Y/Z`, `tpLocationIsOffset`, `canTele`, `findNextChunk`, `repairState`) from the code only.
- Open game SQLite files only through `GameDatabase.open` (read-only). Saves are WAL mode: if `<file>-wal` exists the
  game/server has it open, so it uses plain `mode=ro` (sees unmerged changes, takes the read lock); otherwise
  `immutable=1`, because a plain read-only open of a closed WAL database leaves empty `-wal`/`-shm` files behind.
  Tested with SQLite 3.45 and a writer process; live reads need working file locks (not NFS/SMB).
- `clientsettings.json` stores `modPaths` as absolute paths that can point to another machine's folders, so
  the launcher passes `--addModPath` for the instance's mod path when it is not listed.
- "Last opened" of an instance is the mtime of its `clientsettings.json`.
- Mods: `modinfo.json` is often not strict JSON (comments, trailing commas); icon is `modicon.png` in the
  zip; `game`, `survival`, `creative`, `essentials` dependencies are built in and not on the mod database;
  the API's first release is treated as the latest (may be a pre-release).

## Known gaps
- GUI changes are usually unverified here (headless); the system tray in particular depends on the desktop
  (GNOME needs an extension; macOS AWT tray + JavaFX can be quirky).
- No automated tests. Pure logic worth covering first: `MapCoordinates`, `HumanTime`, `ModManifest`,
  `Protobuf`/`VintageStoryMap.decode`, `SaveGameScanner.parse`, `MapRegistry.recordSession`.
- Instance editing (info page in-line cells, `InstanceModPathsPage`) is unverified in the GUI; the game crashes on a repeated `--addModPath`, so the launcher passes one flag followed by all folders (`--addModPath a b`); verified on game 1.22.7 (Linux), where it appends to the game's own list. The game also searches `<runtime>/Mods`. A mod ID found in several folders is loaded once (highest version) with a client warning.
- Untracked sample/scratch files may sit in the repo root (`Maps/`, `Logs/`, `vs-manifest.json`); `/Maps/`
  and `/Logs/` are gitignored. Never commit game data.
