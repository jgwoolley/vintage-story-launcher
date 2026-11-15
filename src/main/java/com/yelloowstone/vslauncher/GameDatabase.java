package com.yelloowstone.vslauncher;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens the game's SQLite files (saves, maps) read-only, never writing to them.
 * <p>
 * The game keeps them in WAL mode: while the game or a server has one open, recent changes sit in {@code <file>-wal}
 * until they are merged back. If that file exists the database is read with plain {@code mode=ro}, which reads the WAL
 * too and takes SQLite's read lock (so a merge during a long read cannot hand us a half-updated file). Otherwise it is
 * opened {@code immutable}: a plain read-only open of a closed WAL database leaves empty {@code -wal}/{@code -shm}
 * files in the game's folder, immutable leaves nothing. (Tested with SQLite 3.45 on a 1.22.7 save.)
 * <p>
 * Live reads rely on file locks: fine on the same machine or a Docker volume, not on NFS/SMB shares.
 */
public final class GameDatabase {
    private GameDatabase() {
    }

    /** Whether the game (or a server) seems to have the file open, or crashed with changes not merged yet. */
    public static boolean isLive(final File file) {
        return new File(file.getPath() + "-wal").exists();
    }

    public static Connection open(final File file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toURI() + (isLive(file) ? "?mode=ro" : "?mode=ro&immutable=1"));
    }
}
