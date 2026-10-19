package com.yelloowstone.vslauncher;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

/** A client-side world map cache (Maps/&lt;savegame id&gt;.db): 32x32 pixel tiles keyed by chunk position. */
public class VintageStoryMap {
	public static final int TILE_SIZE = 32;
	/** Tile positions are stored as (tileZ << 27) | tileX. */
	private static final int X_BITS = 27;

	/** One map tile; {@code pixels} are ARGB, row by row, TILE_SIZE * TILE_SIZE of them. */
	public record Piece(int tileX, int tileZ, int[] pixels, int version, long lastModified) {
	}

	/** A rendered overview of the map; pixels outside explored tiles are transparent (0). */
	public record Image(int width, int height, int[] argb, int pixelsPerSample, int minTileX, int minTileZ) {
	}

	private final File file;

	public VintageStoryMap(File file) {
		this.file = file;
	}

	public File getFile() {
		return file;
	}

	/** The savegame identifier: the file name without ".db". */
	public String getId() {
		final String name = file.getName();
		return name.endsWith(".db") ? name.substring(0, name.length() - 3) : name;
	}

	private Connection open() throws SQLException {
		// immutable: never create -wal/-shm files next to (or write to) the game's own data
		return DriverManager.getConnection("jdbc:sqlite:" + file.toURI() + "?mode=ro&immutable=1");
	}

	/** Streams every tile to {@code consumer}; unreadable tiles are skipped. */
	public void forEachPiece(final Consumer<Piece> consumer) throws SQLException {
		try (Connection connection = open();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT position, data FROM mappiece")) {
			while (rs.next()) {
				final long position = rs.getLong("position");
				final Piece piece = decode(position, rs.getBytes("data"));
				if (piece != null) {
					consumer.accept(piece);
				}
			}
		}
	}

	/** Streams the tiles in the inclusive tile rectangle; used to draw zoomed-in views on demand. */
	public void loadPieces(final int minTileX, final int minTileZ, final int maxTileX, final int maxTileZ,
			final Consumer<Piece> consumer) throws SQLException {
		try (Connection connection = open();
				java.sql.PreparedStatement statement = connection
						.prepareStatement("SELECT position, data FROM mappiece WHERE position BETWEEN ? AND ?")) {
			for (int z = minTileZ; z <= maxTileZ; z++) {
				statement.setLong(1, ((long) z << X_BITS) | minTileX);
				statement.setLong(2, ((long) z << X_BITS) | maxTileX);
				try (ResultSet rs = statement.executeQuery()) {
					while (rs.next()) {
						final Piece piece = decode(rs.getLong(1), rs.getBytes(2));
						if (piece != null) {
							consumer.accept(piece);
						}
					}
				}
			}
		}
	}

	static Piece decode(final long position, final byte[] data) {
		if (data == null) {
			return null;
		}
		try {
			final int[] pixels = new int[TILE_SIZE * TILE_SIZE];
			int count = 0;
			int version = 0;
			long lastModified = 0;
			for (final Protobuf.Field field : Protobuf.parse(data)) {
				if (field.id() == 1 && field.bytes() == null && count < pixels.length) {
					// Vintage Story stores each pixel as ABGR; convert to ARGB for JavaFX/ImageIO.
					final int abgr = (int) field.number();
					pixels[count++] = (abgr & 0xff00ff00) | ((abgr & 0xff) << 16) | ((abgr >>> 16) & 0xff);
				} else if (field.id() == 2 && field.bytes() == null) {
					version = (int) field.number();
				} else if (field.id() == 3 && field.bytes() == null) {
					lastModified = field.number();
				}
			}
			if (count != pixels.length) {
				return null;
			}
			return new Piece((int) (position & ((1L << X_BITS) - 1)), (int) (position >> X_BITS), pixels, version,
					lastModified);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Draws the explored area, sampling every n-th pixel so the longest side is at most {@code maxSize}.
	 * Returns null when the map has no readable tiles.
	 */
	public Image render(final int maxSize) throws SQLException {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		try (Connection connection = open();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT position FROM mappiece")) {
			while (rs.next()) {
				final long position = rs.getLong(1);
				final int x = (int) (position & ((1L << X_BITS) - 1));
				final int z = (int) (position >> X_BITS);
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
			}
		}
		if (minX > maxX) {
			return null;
		}

		final int fullWidth = (maxX - minX + 1) * TILE_SIZE;
		final int fullHeight = (maxZ - minZ + 1) * TILE_SIZE;
		final int step = Math.max(1, (int) Math.ceil(Math.max(fullWidth, fullHeight) / (double) maxSize));
		final int width = (fullWidth + step - 1) / step;
		final int height = (fullHeight + step - 1) / step;
		final int[] argb = new int[width * height];
		// With step > 1 each output pixel is the average of the samples that fall in it
		final int[] sums = step > 1 ? new int[width * height * 4] : null;
		final int[] counts = step > 1 ? new int[width * height] : null;

		final int originX = minX;
		final int originZ = minZ;
		forEachPiece(piece -> {
			final int left = (piece.tileX() - originX) * TILE_SIZE;
			final int top = (piece.tileZ() - originZ) * TILE_SIZE;
			// Samples sit on a global grid so neighbouring tiles line up
			for (int y = (step - top % step) % step; y < TILE_SIZE; y += step) {
				for (int x = (step - left % step) % step; x < TILE_SIZE; x += step) {
					final int out = ((top + y) / step) * width + (left + x) / step;
					final int pixel = piece.pixels()[y * TILE_SIZE + x];
					if (sums == null) {
						argb[out] = pixel;
					} else {
						sums[out * 4] += pixel >>> 24;
						sums[out * 4 + 1] += (pixel >> 16) & 0xff;
						sums[out * 4 + 2] += (pixel >> 8) & 0xff;
						sums[out * 4 + 3] += pixel & 0xff;
						counts[out]++;
					}
				}
			}
		});
		if (sums != null) {
			for (int i = 0; i < argb.length; i++) {
				if (counts[i] > 0) {
					final int n = counts[i];
					argb[i] = (sums[i * 4] / n << 24) | (sums[i * 4 + 1] / n << 16) | (sums[i * 4 + 2] / n << 8)
							| sums[i * 4 + 3] / n;
				}
			}
		}
		return new Image(width, height, argb, step, minX, minZ);
	}
}
