# Research notes

Findings and open ideas that are not (yet) implemented. Items marked *unverified* were reasoned from how
Linux desktops and `jpackage` work, not tested on a real Ubuntu desktop.

## Moving the launcher to TypeScript / Electron (planned, not started)

The launcher could join the sibling `vintage-story-wiki` TypeScript workspace (`vsdev`, wiki, VS Code extension) as an
Electron app, sharing a save reader (`libs/vs-save`) and mod management (`libs/vs-mods`) as plain imports. Full phased
plan, risks and open questions: [`NODE_MIGRATION_PLAN.md`](NODE_MIGRATION_PLAN.md). Phase 1 (`vs-save` + `vsdev saves`)
is useful even if the launcher itself never moves.

## Right-click menu on the app icon (Ubuntu) vs. the system tray

These are two different features, and only the tray is implemented (`gui/TrayMenu.java`).

| | System tray icon | Right-click on the dock / app grid icon |
|---|---|---|
| Created by | the running app (`java.awt.SystemTray`) | the app's `.desktop` file (`Actions=` entries, "quick actions") |
| Exists when | the app is running | always, even when the app is not running |
| Content | dynamic (we list the 5 most recent instances) | fixed text defined at package time |

### Why there is no right-click menu today
- `jpackage --type deb` generates a `.desktop` file with no `Actions=` entries, so Ubuntu only shows its
  default items (e.g. Quit while running).
- The generated file also has no `StartupWMClass`. *Unverified:* the dock may then fail to match the Java
  window to the installed app, which would explain a generic icon and missing options on the running app.
  The JavaFX window class is probably derived from the main class (`com-yelloowstone-vslauncher-Launcher`);
  check it with `xprop WM_CLASS` (click the window) before relying on this.

### Why the tray icon may not show on Ubuntu
- GNOME removed legacy tray icons. They only appear with the AppIndicator extension
  (`gnome-shell-extension-appindicator`; Ubuntu often ships it but it can be disabled).
- Without it `SystemTray.isSupported()` can be false or the icon is invisible; the launcher then just runs
  without the tray. KDE and most other desktops show the icon.
- Settings page disables the "keep running in the tray" checkbox when the tray is unsupported.

### Options
1. **Quick actions (static):** override the `.desktop` file via `jpackage --resource-dir` and add
   actions such as "Load Last Instance" and "Show Launcher".
   - Desktop actions are fixed text, so the "5 most recent instances" list cannot be shown there.
   - Needs command-line handling in the app, e.g. `--open-last` to launch the last instance without
     showing the window. `Application.getParameters()` receives the arguments; `jpackage` passes extra
     arguments given at launch to the main class.
   - If the launcher is already running, a second start opens a second window. Decide whether to add
     single-instance handling (e.g. a lock file or local socket that forwards the action to the running app).
2. **`StartupWMClass`:** add it to the `.desktop` file (same `--resource-dir` override) so the dock matches the
   window to the app. Set the same value from Java if the window class needs to be fixed.
3. **Tray on GNOME:** nothing to code; document that the AppIndicator extension is needed.

### How to override the desktop file
- Put a template named after the launcher (`Nf3t’s VintageStory Launcher.desktop`) in a resource directory
  and pass `--resource-dir <dir>` to `jpackage` in `build.sh` (and the Linux jobs of
  `.github/workflows/build.yml`).
- The default template uses placeholders (e.g. `APPLICATION_NAME`, `APPLICATION_LAUNCHER`,
  `APPLICATION_ICON`); copy the one from the JDK's `jdk.jpackage` module and add the extra keys, then check
  the package contents with `dpkg-deb -x` and `sh -n` on the generated `postinst`/`prerm`.
- The display name contains non-ASCII `’` and spaces; keep the maintainer scripts shell-safe (see
  `CLAUDE.md`).

### To check first
- Is the tray icon visible with the AppIndicator extension enabled? That decides how much to invest in the tray
  versus quick actions.
- `xprop WM_CLASS` of the running launcher window, to pick the right `StartupWMClass`.

## Server name <-> savegame GUID <-> host / port mapping

Goal: for each `Maps/<guid>.db`, know which server it was played on, and show the label the user gave that
server in the game's multiplayer list.

### The three identifiers
| Identifier | Where it lives | Status |
|---|---|---|
| Savegame GUID | `Maps/<guid>.db` file name; `World.SavegameIdentifier` in the client API; for single player also `Saves/*.vcdbs` | Known. The launcher reads it (`VintageStoryMap`, `SaveGameScanner`). |
| `host-port` / `host` | `Logs/client-main.log` of the latest session: `ModsByServer/<host-port>` and `Connecting to <host>...` lines | Known from the launcher's regexes (`MapRegistry.MODS_BY_SERVER`, `CONNECTING`). Not re-checked against a real log in this research pass. |
| Server label (the name the user typed in the multiplayer list) | The game's client settings, possibly `clientsettings.json`, with `clientsettings.bkp` mentioned in earlier notes | **Not verified.** Its format, and whether it stores host and port, was not read. |

### How the launcher links GUID <-> server today
`MapRegistry.recordSession`, run when the game started by the launcher exits:
1. Snapshot `Maps/*.db` modification times before launch; after exit, collect maps that are new or newer.
2. Drop maps that match a single player save (`SaveGameScanner`).
3. Read the servers named in `Logs/client-main.log` (only the latest session survives); drop a bare `host` when
   a `host-port` entry exists.
4. If exactly one map changed and exactly one server was logged, store it in the launcher's `maps.json`.
   Otherwise log "Could not tell which server ... belongs to ..." and store nothing.

So a mapping exists only for a server joined at least once from the launcher, in a session with exactly one
changed map and one server. The result is `host-port`, not the user's label.

When the user adds the server's data folder as a server instance, the link is exact instead: the server save's
`SavegameIdentifier` is the client map's file name (`Context.getServerWorlds`), and it wins over the log guess.
Ideas not done yet for server maps:
- An "only on my explored map" filter (the server lists structures in land the chosen client never saw).
- Remember "None" as a choice (today an unset choice falls back to the biggest client map).
- Use a matching server instance's save on the client's own map page too, instead of only on the server's page.
- Remote servers: run the headless `search ... --format json` over `ssh` rather than mounting the folder.

### Can a mod supply it? (checked, answer: not cleanly)
Reflection over `vs_server/VintagestoryAPI.dll` and `vs_server/VintagestoryLib.dll` for members named
`ServerName` / `ServerIp`:
- `IServerConfig.ServerName`, `IServerAPI.ServerIp`, `Vintagestory.Server.ServerConfig.ServerName` and
  `Vintagestory.Server.ServerAPI.ServerIp` are server side only.
- `Packet_ServerIdentification.ServerName` (VintagestoryLib, internal) carries the server's own name to the
  client. That is the server's name, not the user's label from the multiplayer list.
- No public client API member exposes the connected server's name, host or port. The client mod
  (`Nf3tVSCommon`) can only read `ClientApi.World.SavegameIdentifier`.
- Decision: no separate mod. `Nf3tConfig.json` (mod settings: search radius, block selectors, commands) holds
  no server data; `ModData/Nf3tData.json` is keyed by GUID but has no server field.

### Files consulted
Vintage Story itself: only `VintagestoryAPI.dll` and `VintagestoryLib.dll` (types and member names via
reflection, plus `strings`). No game data was available on this machine (`~/.config/VintagestoryData` does not
exist), so **no real `clientsettings.json`, `clientsettings.bkp` or `client-main.log` was read.**
Project files: `MapRegistry.java`, `VintageStoryInstance.java` (reads `clientsettings.json` for `modPaths` and
`playername`), `CLAUDE.md`, `TODO.md`; in the mod repo `Nf3tVSCommon/Context.cs`, `SaveData.cs`,
`SerializedSaveData.cs`, `Nf3tConfig.cs`.

### To check next (needs a real data folder)
- Open `clientsettings.json` / `clientsettings.bkp` after adding a multiplayer server: where is the label stored,
  and does the entry hold host and port in a form comparable to the log's `host-port`?
- Is `clientsettings.bkp` simply a backup the game writes next to `clientsettings.json`?
- Does the log's `host-port` use the same host spelling (DNS name vs IP) as the saved entry?
- If so, map label <-> `host-port` in the launcher and show the label next to each map.
