# Plan: move the launcher to TypeScript / Electron

Status: **not started** (written 2026-11-15). Nothing here is decided beyond "worth doing, later". Each phase is
useful on its own, so the plan can stop after any of them.

## Why

The launcher is the only Java piece of the Vintage Story tooling (about 7,600 lines). Everything else (`vsdev`, the
wiki, the VS Code extension, `vs-schematic`, `vs-wiki-core`) is one TypeScript npm workspace in the sibling
`vintage-story-wiki` repo. Mod management is written twice (launcher `mods/`, about 660 lines, and `vsdev/src/mods`,
the bigger and more complete one), and the save reader exists only in Java. Moving the launcher into that workspace
makes shared code plain imports: no bundled binaries, no subprocess JSON, no WebAssembly.

Electron, not Tauri: Tauri's backend is Rust and its web view cannot read files, so the TypeScript libraries would sit
behind a Node sidecar again.

## Target layout (in `vintage-story-wiki`)

```
libs/vs-save      save reader: structures, story locations, players + spawn points, waypoints, deaths,
                  ore readings, translocators, traders; search rules; WAL-aware read-only open
libs/vs-mods      mods folder scan, lenient modinfo.json, ModDB API, versions, dependencies, install/update,
                  vs-manifest.json (moved out of apps/vsdev/src/mods + manifest)
apps/vsdev        gains `vsdev saves` (on vs-save); imports vs-mods instead of its own copy
apps/vs-launcher  Electron app (main process: Node, vs-save, vs-mods; renderer: UI)
```

Conventions to follow there: `tsx --test` tests in `<pkg>/test/*.test.ts` (the root `npm test` runs them all),
`npm run typecheck`, Node >= 22, workspaces under `libs/*` and `apps/*`.

## Phase 0: reference data (in the launcher repo, before any porting)

The Java code is the reference the port is checked against, so freeze its behaviour first.

1. Collect test saves into a fixtures folder (in the wiki repo, e.g. `libs/vs-save/test/fixtures/`):
   - the 1.22.7 dedicated server sample (`/tmp/vsfind/data/Saves/default.vcdbs` this session: 1372 structures,
     6 story locations, 3 traders), trimmed if it is too big to commit (keep the `gamedata`, `playerdata`,
     `mapregion` rows and a few chunks with traders);
   - the synthetic save from this session's GUI tests (players, a spawn point, waypoints, a death, an ore reading,
     a linked translocator pair and a broken one). Its generator script should be committed, not just the file;
   - a real save with waypoints, deaths, ore readings and translocators, as soon as one exists (these readers are
     still **unverified** against real data).
2. For each fixture, record `java -jar vintagestory-launcher-<v>.jar search <fixture> --format json` (all kinds, plus
   a few filtered runs: `--same player:...`, `--pair`, `--near/--radius`) as golden files.
3. Move the save format notes from the launcher's `CLAUDE.md` ("Vintage Story data gotchas") into a
   `docs/save-format.md` in the wiki repo; the launcher's `CLAUDE.md` links to it. Include the WAL / read-only
   findings (`GameDatabase`).

Done when: fixtures + golden JSON are in the wiki repo and the Java CLI reproduces them.

## Phase 1: `libs/vs-save` and `vsdev saves`

Port, in this order (Java source -> TS module):

| Java | TS | Notes |
|---|---|---|
| `Protobuf` | `protobuf.ts` | include wire type 1 (`doubles`) and the fixed `skip` (length read before advancing) |
| `NetBinaryReader` | reuse `vs-schematic`'s TreeAttribute reader if it can be exported; otherwise `netBinary.ts` | ids 1-16, 7-bit length strings |
| `GameDatabase` | `database.ts` | `<file>-wal` exists -> `mode=ro`, else `immutable=1`; never write |
| `SaveGameScanner` | `scanner.ts` | save id, world name, size, spawn (spawn layout still unverified) |
| `SaveGameStructures` | `structures.ts`, `worldData.ts`, `players.ts`, `chunks.ts` | one file per table |
| `SaveSearch` | `search.ts` | same-as, pairs, `/tp`, parseType, read only needed tables |
| `SaveSearchCli` | `apps/vsdev/src/commands/saves.ts` | same options and exit codes; vsdev's own `--json` style |

- SQLite: try `node:sqlite` first (built in, Node 22.5+; check `immutable=1` and `mode=ro` URIs work there). Fallback:
  `better-sqlite3`.
- Chunk scan: stream results (async iterator) so `--limit` can stop early; the Java version cannot yet.
- Tests: parse each fixture, compare with the golden JSON from phase 0 (ignoring row order where sorting ties), plus
  unit tests for protobuf edge cases, TreeAttribute, `parseType`, `/tp`, and the WAL test (a writer process holding
  the save open: the reader sees unmerged rows; a closed save leaves no `-wal`/`-shm` behind).

Done when: `vsdev saves` matches the Java CLI on every golden file, and `npm test` covers it.

## Phase 2: `libs/vs-mods`

1. Move `apps/vsdev/src/mods/*` and the manifest code into `libs/vs-mods`; vsdev imports it (no behaviour change,
   existing `mods.test.ts` / `wiki-manifest.test.ts` keep passing).
2. Compare with the launcher's `mods/` and carry over what vsdev lacks (check each, don't assume):
   - mods of a server (`ModsByServer/<host-port>`), shown read-only;
   - the rule "the API's first release is the latest" (may be a pre-release);
   - built-in dependencies `game`, `survival`, `creative`, `essentials`;
   - several mod folders per instance and the duplicate-mod warning (highest version wins);
   - manifest export as the launcher writes it.

Done when: vsdev and (later) the launcher can do everything the Java `ModsPage` / `ModUpdater` do through vs-mods.

## Phase 3: `apps/vs-launcher` (Electron)

Main process = Node (files, SQLite, network, game process); renderer = UI over IPC. Port page by page, keeping the
Java launcher as the released app until parity.

1. Shell: window, settings (`settings.json`), config in `~/.config/VSLauncher/` read and written **compatibly** with
   the Java version (same `config.json`, `maps.json`, `settings.json`), so both can be used side by side.
2. Instances: list, info page with in-line editing, mod paths, copy, launch (wrapper, environment, a single
   `--addModPath a b`), "last opened", session tracking -> `maps.json` (server <-> map).
3. Maps: list, overview rendering, the pan/zoom viewer (canvas; tiles are ABGR, `(z << 27) | x` positions), the save
   table (kinds, search, paging, Same ... / Show Pair / Copy /tp, click a mark to select, translocator links).
   New here: a 3D preview of a structure's schematic with `vs-schematic`.
4. Mods: through vs-mods.
5. Tray (Electron `Tray`; GNOME still needs the AppIndicator extension), close to tray, recent instances.
6. Packaging: electron-builder for `.deb`, Windows and macOS on `v*` tags; check the display name with the
   typographic apostrophe; `.desktop` file with `StartupWMClass` and quick actions (see `RESEARCH.md`).
7. Cut-over: when every page matches, release it as the next major version; the Java launcher becomes legacy (keep
   its `search` CLI working until `vsdev saves` replaces it in docs).

Privacy (standing rule): no telemetry, crash reporter off, strict CSP, no remote content in the renderer,
`contextIsolation` on and no Node in the renderer, network only to the mod database and game downloads.

## Risks and checks to do early

- `node:sqlite` inside Electron's Node build (it may be disabled there); otherwise `better-sqlite3`, rebuilt per
  Electron version.
- Read-only + live (WAL) semantics repeated with the chosen driver (phase 1 test).
- Big saves (several GB): the chunk scan must stream and stay off the UI (main process or a worker thread).
- Installer size: about 90-120 MB, against 50-70 MB of bundled Java runtime today.
- The unverified readers (waypoints, deaths, ore readings, translocators, player spawn layout) should be checked on a
  real save before or during phase 1, so the port does not copy a mistake twice.

## Open questions

- Do both launchers need to run against the same config during the transition, or can the new one migrate it once?
- Should the browser save viewer on the wiki (vs-save + wa-sqlite / sql.js) be planned as a phase 4?
- Should the new launcher live in the wiki repo for good, or move with the libs into a dedicated "vintage-story-tools"
  repo once the wiki is only one of several apps?
