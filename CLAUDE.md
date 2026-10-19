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
- This dev container is headless: the GUI cannot be launched here. Test non-GUI logic with small
  throwaway `main` classes against `target/classes` (get the classpath with
  `mvn dependency:build-classpath`), and say so when a GUI change is unverified.

## Layout (`src/main/java/com/yelloowstone/vslauncher`)
The Java package is `com.yelloowstone.vslauncher` (typo'd "yelloowstone"); **never rename it**. The brand is
"Nf3t" everywhere else (README, display name, vendor). Maven groupId is still `com.yelloowstone.nf2t`.

- `App`, `Launcher` – JavaFX entry (`Launcher` is the jar's main class so the shaded JAR starts without module issues).
- `VintageStoryInstance` – name + runtimePath + dataPath + optional modPath (null = `<dataPath>/Mods`).
  Serialized to `~/.config/VSLauncher/config.json` by Jackson. `open()` runs the game on a daemon thread
  (never block the FX thread) and records which map belongs to which server when the game exits.
- `VintageStoryMap` – a `Maps/<savegame id>.db` SQLite file: reading tiles, overview rendering.
- `SaveGameScanner` – reads `Saves/*.vcdbs` to map savegame id -> single player save (+ spawn / world size).
- `MapRegistry` – `maps.json`: user-given map names and the server each map was played on; session tracking.
- `MapCoordinates` – parses "x z" input (plain = relative to spawn, `=` prefix = absolute, optional ignored y).
- `Settings` – `settings.json` (time zone, relative times, scroll speed, close to tray).
- `Protobuf` – tiny wire-format parser (see gotchas). `ProtobufUtils` + `vintagestory.desc/.proto` are older
  and partly wrong; prefer `Protobuf`.
- `mods/` – `ModUpdater` (scan Mods folder, check updates, resolve dependencies, apply), `ModDbClient`
  (mods.vintagestory.at API), `LocalMod`, `ModManifest` (vs-manifest.json export).
- `gui/` – one class per page (`InstanceSelectionPage`, `InstanceInfoPage`, `ModsPage`, `MapsInfoPage`,
  `MapInfoPage`, `SettingsPage`, ...) building into `context.getRootNode()`; `Context` holds shared state.
  Helpers: `Icons.button(text)` (use instead of `new Button`), `HumanTime` (all date rendering),
  `MapViewer` (pan/zoom canvas), `TrayMenu` (system tray).

## Conventions
- Match the surrounding code: gui pages are static `create(Context[, ...])` methods that clear and refill
  the root node; play `context.getVolumeProperty().play()` in button handlers.
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
- Open game SQLite files read-only and immutable (`jdbc:sqlite:file:...?mode=ro&immutable=1`); a normal open
  creates `-wal`/`-shm` files in the game's folders.
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
- Existing instances cannot have their mod path edited after creation (create a new instance).
- Untracked sample/scratch files may sit in the repo root (`Maps/`, `Logs/`, `vs-manifest.json`); `/Maps/`
  and `/Logs/` are gitignored. Never commit game data.
