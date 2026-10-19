# Research notes

Findings and open ideas that are not (yet) implemented. Items marked *unverified* were reasoned from how
Linux desktops and `jpackage` work, not tested on a real Ubuntu desktop.

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
