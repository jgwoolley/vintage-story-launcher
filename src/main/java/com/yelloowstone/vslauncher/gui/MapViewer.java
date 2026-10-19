package com.yelloowstone.vslauncher.gui;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.yelloowstone.vslauncher.MapCoordinates;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Pan (drag) and zoom (mouse wheel) view of a map. Zoomed out it draws a pre-rendered overview;
 * zoomed in it loads the tiles in view from the map file on demand.
 */
public class MapViewer extends Region {
	private static final int TILE = VintageStoryMap.TILE_SIZE;
	private static final int X_BITS = 27;
	private static final double MAX_SCALE = 16; // screen pixels per block
	private static final int MAX_TILES_DRAWN = 3000;
	private static final int MAX_CACHED_TILES = 8000;

	private final Canvas canvas = new Canvas();
	private final VintageStoryMap map;
	private final MapCoordinates coordinates;
	private final Consumer<String> onHover;

	private VintageStoryMap.Image overview;
	private WritableImage overviewImage;
	private double centerX;
	private double centerZ;
	private double scale = 1;
	private double minScale = 0.01;
	private Double markerX;
	private Double markerZ;
	private double dragX;
	private double dragZ;

	private final Map<Long, int[]> loaded = new ConcurrentHashMap<>();
	private final Set<Long> requested = ConcurrentHashMap.newKeySet();
	private final Map<Long, WritableImage> images = new LinkedHashMap<>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(final Map.Entry<Long, WritableImage> eldest) {
			return size() > 4096;
		}
	};
	private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
		final Thread thread = new Thread(r, "vs-map-tiles");
		thread.setDaemon(true);
		return thread;
	});

	public MapViewer(final VintageStoryMap map, final MapCoordinates coordinates, final Consumer<String> onHover) {
		this.map = map;
		this.coordinates = coordinates;
		this.onHover = onHover;
		getChildren().add(canvas);
		setMinSize(0, 0);
		setStyle("-fx-background-color: #2b1d17;");

		setOnMousePressed(e -> {
			dragX = e.getX();
			dragZ = e.getY();
		});
		setOnMouseDragged(e -> {
			centerX -= (e.getX() - dragX) / scale;
			centerZ -= (e.getY() - dragZ) / scale;
			dragX = e.getX();
			dragZ = e.getY();
			redraw();
		});
		setOnMouseMoved(e -> onHover.accept(coordinates.describe(worldX(e.getX()), worldZ(e.getY()))));
		setOnScroll(e -> {
			if (overview == null || e.getDeltaY() == 0) {
				return;
			}
			final double beforeX = worldX(e.getX());
			final double beforeZ = worldZ(e.getY());
			scale = Math.max(minScale, Math.min(MAX_SCALE, scale * Math.pow(1.0015, e.getDeltaY())));
			// keep the block under the cursor where it was
			centerX = beforeX - (e.getX() - getWidth() / 2) / scale;
			centerZ = beforeZ - (e.getY() - getHeight() / 2) / scale;
			redraw();
		});
	}

	/** Gives the viewer the pre-rendered overview and frames the explored area. */
	public void setOverview(final VintageStoryMap.Image image) {
		this.overview = image;
		this.overviewImage = new WritableImage(image.width(), image.height());
		overviewImage.getPixelWriter().setPixels(0, 0, image.width(), image.height(),
				PixelFormat.getIntArgbInstance(), image.argb(), 0, image.width());
		fit();
	}

	/** Zooms out to show everything explored. */
	public void fit() {
		if (overview == null) {
			return;
		}
		final double blocksX = overview.width() * overview.pixelsPerSample();
		final double blocksZ = overview.height() * overview.pixelsPerSample();
		centerX = overview.minTileX() * TILE + blocksX / 2;
		centerZ = overview.minTileZ() * TILE + blocksZ / 2;
		final double w = Math.max(1, getWidth());
		final double h = Math.max(1, getHeight());
		minScale = Math.min(w / blocksX, h / blocksZ) * 0.9;
		scale = minScale;
		redraw();
	}

	/** Centres on an absolute block position, zooming in to at least one pixel per block, and marks it. */
	public void goTo(final double x, final double z) {
		markerX = x;
		markerZ = z;
		centerX = x;
		centerZ = z;
		scale = Math.max(scale, 2);
		redraw();
	}

	private double worldX(final double screenX) {
		return centerX + (screenX - getWidth() / 2) / scale;
	}

	private double worldZ(final double screenY) {
		return centerZ + (screenY - getHeight() / 2) / scale;
	}

	private double screenX(final double worldX) {
		return (worldX - centerX) * scale + getWidth() / 2;
	}

	private double screenY(final double worldZ) {
		return (worldZ - centerZ) * scale + getHeight() / 2;
	}

	@Override
	protected void layoutChildren() {
		final boolean first = canvas.getWidth() == 0 && getWidth() > 0 && overview != null;
		canvas.setWidth(getWidth());
		canvas.setHeight(getHeight());
		if (first) {
			fit();
		} else {
			redraw();
		}
	}

	private void redraw() {
		final GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.setFill(Color.web("#2b1d17"));
		gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		if (overview == null) {
			return;
		}

		// Overview underneath, so tiles still loading show something
		final int step = overview.pixelsPerSample();
		gc.setImageSmoothing(scale < 1);
		gc.drawImage(overviewImage, screenX(overview.minTileX() * (double) TILE), screenY(overview.minTileZ() * (double) TILE),
				overview.width() * step * scale, overview.height() * step * scale);

		drawTiles(gc);
		drawMarker(gc);
	}

	private void drawTiles(final GraphicsContext gc) {
		final int firstX = (int) Math.floor(worldX(0) / TILE);
		final int lastX = (int) Math.floor(worldX(getWidth()) / TILE);
		final int firstZ = (int) Math.floor(worldZ(0) / TILE);
		final int lastZ = (int) Math.floor(worldZ(getHeight()) / TILE);
		if ((long) (lastX - firstX + 1) * (lastZ - firstZ + 1) > MAX_TILES_DRAWN) {
			return; // too far out: the overview is enough
		}

		gc.setImageSmoothing(false);
		final double size = TILE * scale + 0.5; // slight overlap avoids hairline gaps
		int missMinX = Integer.MAX_VALUE, missMaxX = Integer.MIN_VALUE, missMinZ = Integer.MAX_VALUE, missMaxZ = Integer.MIN_VALUE;
		for (int tz = firstZ; tz <= lastZ; tz++) {
			for (int tx = firstX; tx <= lastX; tx++) {
				final long key = ((long) tz << X_BITS) | tx;
				final int[] pixels = loaded.get(key);
				if (pixels != null) {
					WritableImage image = images.get(key);
					if (image == null) {
						image = new WritableImage(TILE, TILE);
						image.getPixelWriter().setPixels(0, 0, TILE, TILE, PixelFormat.getIntArgbInstance(), pixels, 0, TILE);
						images.put(key, image);
					}
					gc.drawImage(image, screenX(tx * (double) TILE), screenY(tz * (double) TILE), size, size);
				} else if (requested.add(key)) {
					missMinX = Math.min(missMinX, tx);
					missMaxX = Math.max(missMaxX, tx);
					missMinZ = Math.min(missMinZ, tz);
					missMaxZ = Math.max(missMaxZ, tz);
				}
			}
		}
		if (missMinX <= missMaxX) {
			load(missMinX, missMinZ, missMaxX, missMaxZ);
		}
	}

	private void load(final int minX, final int minZ, final int maxX, final int maxZ) {
		loader.submit(() -> {
			try {
				if (loaded.size() > MAX_CACHED_TILES) {
					loaded.clear();
					requested.clear();
					Platform.runLater(images::clear);
				}
				map.loadPieces(minX, minZ, maxX, maxZ, piece -> loaded.put(((long) piece.tileZ() << X_BITS) | piece.tileX(), piece.pixels()));
			} catch (SQLException e) {
				System.err.println("Could not load map tiles: " + e.getMessage());
			}
			Platform.runLater(this::redraw);
		});
	}

	private void drawMarker(final GraphicsContext gc) {
		if (markerX == null) {
			return;
		}
		final double x = screenX(markerX);
		final double y = screenY(markerZ);
		gc.setStroke(Color.RED);
		gc.setLineWidth(2);
		gc.strokeOval(x - 8, y - 8, 16, 16);
		gc.strokeLine(x - 14, y, x + 14, y);
		gc.strokeLine(x, y - 14, x, y + 14);
	}
}
