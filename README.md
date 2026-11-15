# 🏺 Nf3t's Vintage Story Launcher

[![Release](https://img.shields.io/github/v/release/jgwoolley/vintage-story-launcher?display_name=tag&sort=semver)](https://github.com/jgwoolley/vintage-story-launcher/releases)
[![License](https://img.shields.io/github/license/jgwoolley/vintage-story-launcher)](LICENSE)

A graphical user interface designed to manage multiple instances of **Vintage Story** on a single machine. Enables switching between different mod sets, game versions, or server configurations without manual folder management.

---

## 🚀 Getting Started

To get the latest version of the launcher, visit the **Releases** page. Native installers for all major operating systems and a standalone Fat JAR are provided.

### [👉 Download Latest Release](https://github.com/jgwoolley/vintage-story-launcher/releases/latest)

---

## 📦 Installation Options

Choose the version that best fits your system:

### 🪟 Windows
* **Installer (.msi):** Recommended. Includes a guided setup and adds shortcuts to your Start Menu.
* **Portable (.zip):** No installation required. Extract and run `VSLauncher.exe` directly.

### 🐧 Linux
* **Debian/Ubuntu (.deb):** Best for Ubuntu, Mint, and Debian-based distros.
* **RedHat/Fedora (.rpm):** Best for Fedora, RHEL, and CentOS.

### 🍎 macOS
* **Disk Image (.dmg):** Open the DMG and drag the VS Launcher to your Applications folder.
* Guide for downloading from unknown source: https://support.apple.com/en-eg/guide/mac-help/mh40616/mac

### ☕ Cross-Platform
* **Fat JAR (.jar):** For users who already have **Java 17+** installed. Run via terminal:
    ```bash
    java -jar vintagestory-launcher-<version>.jar
    ```

---

## 🛠 Features
* **Instance Isolation:** Keep mods and save files for different versions completely separate. Each instance has its own runtime, data and mod path.
* **Mod Manager:** See each mod's icon, description, installed version and the latest version on the mod database. Update one mod or all of them, including the dependencies they need. See also the mods a server made the game download.
* **Mod Manifest Export:** Export your mods as a `vs-manifest.json` (with its JSON schema) pinned to the installed versions.
* **Maps:** Name your maps, see whether each is a single player save or which server it came from, and browse a rendered preview with pan, zoom and jump-to-coordinate (relative to spawn, or absolute with `=`). For single player saves, search what the save records (structures, story locations, players and spawn points, waypoints, deaths, prospecting readings, translocators, traders) and jump to it.
* **Servers:** Add a dedicated server's data folder to search its world the same way, shown over the map any of your client instances has of that world.
* **System Tray:** Open any of your 5 most recent instances from the tray icon, and optionally keep the launcher running in the tray when its window is closed (where the desktop has a tray).
* **Settings:** Choose a time zone, show times as "5 min ago" or as dates, and set the scroll speed.
* **Native Performance:** Built using Java 17 and packaged as a native app for low overhead.
* **Simple Interface:** Quickly toggle between your created instances, or load the last one you opened.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

---

## 📖 Using the Launcher

1. **Create an instance:** pick a Runtime Path (the game folder), an Instance Path (data folder) and a Mod Path. Choosing an Instance Path fills in its `Mods` folder.
2. **Open** it from the instance list, use **Load Last Instance**, or pick one of the 5 most recent from the system tray icon. The launcher stays usable while the game runs.
3. **Manage Mods** shows the instance's mods and checks for updates. The dropdown at the top switches to a server's mods (read-only). **Export Manifest** saves `vs-manifest.json`.
4. **Open Maps** lists the maps of an instance. **More** opens a map to rename it and browse it: drag to pan, scroll to zoom, and type a coordinate in **Go to**. Under the map of a single player save, one table lists what the save records, each with its own mark on the map (the same mark is shown by the row): generated structures (explored land only), story locations (Tobias, the Resonance Archive...), players' last saved positions and spawn points, waypoints, last deaths, prospecting readings, translocators (with a line to where a working one leads) and traders (scanned from the world after the rest, so they appear a moment later). Tick or untick each kind under the search, type part of a name or detail (`shrine`, `trader`, `copper`, a player) or a pattern (`*vugs`) to search, click a row to jump to it, **Copy /tp** to copy the in-game teleport command for it, use **Same Structure** / **Same Player** / **Same Trader**... or **Same Schematic** to list only rows like it (**Show All** to go back), page through the table with **Previous** / **Next**, hover a mark on the map for its name, and click a mark to select its row (the table turns to its page). Select a working translocator to see where it leads: the map frames both ends, highlights the link and rings the far end, and the status line gives the destination and distance; **Show Pair** (in place of Same Schematic) lists only it and its other end. Search `translocator` to list them all.
5. **Servers:** **Add Server** and pick a dedicated server's data folder (the one with `serverconfig.json`, `Saves/` and `Playerdata/`; a Docker volume or a copy works too). Its name, port and world are read from there; the launcher never starts it. **Open Maps** on a server shows its world: the search table and marks come from the server's save, the map underneath from a client instance that has played that world (**Client Map** picks which, or **None** for marks only). Clients name that map after the world's ID, so it is found without any setup, and the client's maps list then shows it as that server's. Name maps on the client's own map page. A folder with `serverconfig.json` and no `clientsettings.json` is always a server; otherwise use **Switch to Server** in the instance's **Type** row.
6. **Edit an instance:** on its info page click a value to edit it (name, paths, launch arguments, wrapper command, environment). **Edit Mod Paths** manages several mod folders; the ones from the game's `clientsettings.json` are read-only.

Coordinates follow the game: `100 -250` is relative to spawn, `=512100 =511900` is absolute, and a y value (`x y z`) is accepted and ignored.

### Searching saves from the command line
The same save search works without the window (headless, over SSH, in scripts), on single player saves and on a
dedicated server's data folder:

```
java -jar vintagestory-launcher-<version>.jar search <save> [options]
```

The installed app takes the same arguments. `<save>` is a `.vcdbs` file, a data folder (one with `Saves/` and
`Playerdata/`, e.g. a server's), or a world, save or instance name from your instances; `search --list` shows them.

```
search "My World" -t trader                          # traders, nearest to spawn first
search /srv/vintagestory/data -t translocator --format json
search "My World" -q copper -t ore --near "0 0" --radius 500
search "My World" --same player:Yelloow              # a player's position, spawn point and last death
search "My World" --pair "50 -10"                    # a translocator and where it leads
search "My World" -t story --format tp | head -1     # a /tp command to paste into the game
```

Options: `-q` search text or `*` pattern, `-t` kinds (structure, story, player, spawn, waypoint, death, translocator,
ore, trader), `--same kind:name`, `--schematic`, `--pair x z`, `--near x z`, `--radius`, `--sort distance|name|type|y`,
`--limit`, `--format table|tsv|json|tp`, `--no-chunks` (skip the slow world scan: no translocators or traders),
`--backup` (search the newest backup instead), `--world` (pick a save in a folder with several). See `search --help`.
It only ever reads. A save the game or a server has open is read live, including changes not yet written to the main
file (on the same machine or a Docker volume; for a network share, search a `/genbackup` backup). Exit code 0 when
something matched, 1 when nothing did, 2 on errors.

### Where the launcher keeps its files
In `~/.config/VSLauncher/`:

| File | Contents |
|---|---|
| `config.json` | your instances (and server instances, with the client map last shown for each) |
| `maps.json` | map names and the server each map was played on |
| `settings.json` | time zone, relative times and scroll speed |

---

## 🧱 Building from Source

Requires **JDK 17+**, **Maven**, and for the `.deb` also `fakeroot` and the JDK `jmods` (on Ubuntu/Debian: `sudo apt install maven fakeroot openjdk-<version>-jmods`).

```bash
mvn clean verify   # build the fat JAR in target/
./build.sh         # also package a .deb (Linux)
```

`build.sh` prints the commands to install the package. If the same version is already installed, `apt` skips it; remove it first or use `sudo dpkg -i`.

---

## 🤝 Contributing
Contributions are welcome! Please feel free to submit a Pull Request or open an issue for bug reports and feature requests.
