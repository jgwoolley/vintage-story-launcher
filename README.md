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
* **Maps:** Name your maps, see whether each is a single player save or which server it came from, and browse a rendered preview with pan, zoom and jump-to-coordinate (relative to spawn, or absolute with `=`).
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
4. **Open Maps** lists the maps of an instance. **More** opens a map to rename it and browse it: drag to pan, scroll to zoom, and type a coordinate in **Go to**.

Coordinates follow the game: `100 -250` is relative to spawn, `=512100 =511900` is absolute, and a y value (`x y z`) is accepted and ignored.

### Where the launcher keeps its files
In `~/.config/VSLauncher/`:

| File | Contents |
|---|---|
| `config.json` | your instances |
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
