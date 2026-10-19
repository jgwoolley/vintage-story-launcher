# Changelog

All notable changes since 1.0.0. Versions follow `pom.xml`.

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
