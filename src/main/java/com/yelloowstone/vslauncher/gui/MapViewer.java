package com.yelloowstone.vslauncher.gui;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.yelloowstone.vslauncher.MapCoordinates;
import com.yelloowstone.vslauncher.SaveGameStructures.Structure;
import com.yelloowstone.vslauncher.SaveGameStructures.Type;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.application.Platform;
import javafx.scene.Node;
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
	/** The map drawn underneath; null draws only the marks (a server world no client has a map of). */
	private volatile VintageStoryMap map;
	private final MapCoordinates coordinates;
	private final Consumer<String> onHover;
	private Consumer<Structure> onPick;
	private Structure link;

	private VintageStoryMap.Image overview;
	private WritableImage overviewImage;
	private double centerX;
	private double centerZ;
	private double scale = 1;
	private double minScale = 0.01;
	private Double markerX;
	private Double markerZ;
	private List<Structure> structures = List.of();
	private boolean showStructures = true;
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
		// a click (not the end of a drag) on or near a mark picks it
		setOnMouseClicked(e -> {
			if (!e.isStillSincePress() || onPick == null) {
				return;
			}
			final Structure near = structureAt(e.getX(), e.getY());
			if (near != null) {
				onPick.accept(near);
			}
		});
		setOnMouseMoved(e -> {
			final String position = coordinates.describe(worldX(e.getX()), worldZ(e.getY()));
			final Structure near = structureAt(e.getX(), e.getY());
			onHover.accept(near == null ? position : position + "   " + near.type().label() + ": " + near.kind()
					+ (!near.schematic().isEmpty() ? " (" + near.schematic() + ")" : near.detail().isEmpty() ? "" : " (" + near.detail() + ")"));
		});
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

	/** Player colours, picked to stand apart from each other and from the gold structure marks. */
	private static final List<Color> PLAYER_PALETTE = List.of(Color.web("#3fa9ff"), Color.web("#ff4f6d"),
			Color.web("#4cd964"), Color.web("#c77dff"), Color.web("#00e5d4"), Color.web("#ff7ab8"), Color.web("#9aa0ff"),
			Color.web("#7fff00"), Color.web("#ffffff"), Color.web("#ff3b3b"));
	private Map<String, Color> playerColors = Map.of();

	/** Gives every player name its own colour (palette order by name, then spread hues), for the map and the table. */
	public void setPlayers(final List<String> names) {
		final Map<String, Color> colors = new HashMap<>();
		final List<String> sorted = names.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
		for (int i = 0; i < sorted.size(); i++) {
			colors.put(sorted.get(i), i < PLAYER_PALETTE.size() ? PLAYER_PALETTE.get(i)
					: Color.hsb((90 + (i - PLAYER_PALETTE.size()) * 47) % 360, 0.7, 1.0));
		}
		this.playerColors = colors;
		redraw();
	}

	/** The colour of a player's mark (see {@link #setPlayers}). */
	public Color playerColor(final String name) {
		return playerColors.getOrDefault(name, PLAYER_PALETTE.get(0));
	}

	/** Generated structures to mark on the map (from the save), drawn while {@link #setShowStructures} is on. */
	public void setStructures(final List<Structure> structures) {
		this.structures = List.copyOf(structures);
		redraw();
	}

	public void setShowStructures(final boolean show) {
		this.showStructures = show;
		redraw();
	}

	/** The structure whose mark is under a screen point (within a few pixels), or null. */
	private Structure structureAt(final double sx, final double sy) {
		if (!showStructures) {
			return null;
		}
		// the mark drawn on top wins (a trader over its building), then the nearest
		Structure best = null;
		int bestLayer = -1;
		double bestDistance = Double.MAX_VALUE;
		for (final Structure s : structures) {
			final double dx = screenX(s.centerX()) - sx;
			final double dy = screenY(s.centerZ()) - sy;
			final double d = dx * dx + dy * dy;
			if (d > 8 * 8) {
				continue;
			}
			final int layer = layer(s.type());
			if (layer > bestLayer || (layer == bestLayer && d < bestDistance)) {
				bestLayer = layer;
				bestDistance = d;
				best = s;
			}
		}
		return best;
	}

	/** How high a kind is drawn: players on top of everything, then {@link #DRAW_ORDER} backwards. */
	private static int layer(final Type type) {
		return type == Type.PLAYER ? DRAW_ORDER.size() : DRAW_ORDER.indexOf(type);
	}

	/**
	 * Shows another map underneath (or none), keeping the marks; tiles of the old map are dropped. The caller
	 * renders the new overview and passes it to {@link #setOverview} (or {@link #showBlank} when there is no map).
	 */
	public void setMap(final VintageStoryMap map) {
		this.map = map;
		loaded.clear();
		requested.clear();
		images.clear();
		redraw();
	}

	public VintageStoryMap getMap() {
		return map;
	}

	/**
	 * No map: an empty background over the given block area (the marks' extent), framed like an explored map.
	 */
	public void showBlank(final int minX, final int minZ, final int maxX, final int maxZ) {
		final int minTileX = Math.floorDiv(minX, TILE);
		final int minTileZ = Math.floorDiv(minZ, TILE);
		final int span = Math.max(Math.max(maxX - minTileX * TILE, maxZ - minTileZ * TILE), 4 * TILE) + TILE;
		this.overview = new VintageStoryMap.Image(1, 1, new int[] { 0 }, span, minTileX, minTileZ);
		this.overviewImage = new WritableImage(1, 1);
		fit();
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

	/** Called with the mark under a click on the map (none: clicks away from marks do nothing). */
	public void setOnPick(final Consumer<Structure> onPick) {
		this.onPick = onPick;
	}

	/**
	 * Highlights a translocator's link (a bright line to its other end, which gets a ring), or clears the highlight
	 * ({@code null}, or a row without a link). With {@code frame}, also zooms and pans so both ends are in view.
	 */
	public void showLink(final Structure translocator, final boolean frame) {
		this.link = translocator == null || translocator.link() == null ? null : translocator;
		if (link != null && frame) {
			final double ax = link.centerX(), az = link.centerZ();
			final double bx = link.link()[0] + 0.5, bz = link.link()[2] + 0.5;
			centerX = (ax + bx) / 2;
			centerZ = (az + bz) / 2;
			final double spanX = Math.abs(ax - bx) + 40, spanZ = Math.abs(az - bz) + 40;
			scale = Math.max(minScale, Math.min(2, Math.min(getWidth() / spanX, getHeight() / spanZ) * 0.8));
		}
		redraw();
	}

	/** Marks an absolute block position without moving the view. */
	public void mark(final double x, final double z) {
		markerX = x;
		markerZ = z;
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

		try {
			drawTiles(gc);
		} catch (RuntimeException e) {
			// never let a bad tile hide the marks drawn after it
			System.err.println("Could not draw map tiles: " + e);
		}
		drawStructures(gc);
		drawMarker(gc);
		drawLink(gc);
		drawPlayers(gc);
	}

	/** Order the kinds are drawn in (later on top); players are drawn by {@link #drawPlayers}, above the jump marker. */
	private static final List<Type> DRAW_ORDER = List.of(Type.STRUCTURE, Type.ORE, Type.STORY, Type.TRANSLOCATOR,
			Type.TRADER, Type.WAYPOINT, Type.SPAWN, Type.DEATH);

	/** Every mark but players; zoomed in far enough, structures also show their outline. */
	private void drawStructures(final GraphicsContext gc) {
		if (!showStructures || structures.isEmpty()) {
			return;
		}
		final double w = getWidth();
		final double h = getHeight();
		gc.setLineWidth(1);
		// translocator links first, underneath everything
		gc.setStroke(Color.web(TRANSLOCATOR_COLOR, 0.6));
		gc.setLineDashes(6, 4);
		for (final Structure s : structures) {
			if (s.type() == Type.TRANSLOCATOR && s.link() != null) {
				gc.strokeLine(screenX(s.centerX()), screenY(s.centerZ()), screenX(s.link()[0] + 0.5), screenY(s.link()[2] + 0.5));
			}
		}
		gc.setLineDashes((double[]) null);
		for (final Type type : DRAW_ORDER) {
			for (final Structure s : structures) {
				if (s.type() != type) {
					continue;
				}
				final double x = screenX(s.centerX());
				final double y = screenY(s.centerZ());
				if (x < -20 || y < -20 || x > w + 20 || y > h + 20) {
					continue;
				}
				if (scale >= 1 && (type == Type.STRUCTURE || type == Type.STORY)) {
					gc.setStroke(Color.web(type == Type.STORY ? STORY_COLOR : STRUCTURE_COLOR, 0.8));
					gc.setLineWidth(type == Type.STORY ? 2 : 1);
					gc.strokeRect(screenX(s.x1()), screenY(s.z1()), (s.x2() - s.x1() + 1) * scale, (s.z2() - s.z1() + 1) * scale);
					gc.setLineWidth(1);
				}
				drawMark(gc, s, x, y);
			}
		}
	}

	private static final String STRUCTURE_COLOR = "#ffcc33";
	private static final String STORY_COLOR = "#b57bff";
	private static final String TRANSLOCATOR_COLOR = "#00e5ff";
	private static final String ORE_COLOR = "#c87533";
	private static final String TRADER_COLOR = "#4cd964";
	private static final Color OUTLINE = Color.web("#2b1d17");

	/** One mark, centred on a screen point; each kind has its own shape (shared with the table's {@link #legend}). */
	private void drawMark(final GraphicsContext gc, final Structure s, final double x, final double y) {
		gc.setLineWidth(1);
		switch (s.type()) {
		case STRUCTURE -> diamond(gc, x, y, 4, Color.web(STRUCTURE_COLOR), OUTLINE);
		case STORY -> diamond(gc, x, y, 7, Color.web(STORY_COLOR), Color.WHITE);
		case PLAYER -> {
			gc.setFill(playerColor(s.kind()));
			gc.fillOval(x - 5, y - 5, 10, 10);
			gc.setStroke(Color.WHITE);
			gc.setLineWidth(2);
			gc.strokeOval(x - 5, y - 5, 10, 10);
		}
		case SPAWN -> { // a house: square with a roof, in the player's colour
			final double[] xs = { x - 5, x - 5, x, x + 5, x + 5 };
			final double[] ys = { y + 5, y - 1, y - 6, y - 1, y + 5 };
			gc.setFill(playerColor(s.kind()));
			gc.fillPolygon(xs, ys, 5);
			gc.setStroke(Color.WHITE);
			gc.strokePolygon(xs, ys, 5);
		}
		case DEATH -> { // a cross in the player's colour
			gc.setLineWidth(4);
			gc.setStroke(OUTLINE);
			gc.strokeLine(x - 5, y - 5, x + 5, y + 5);
			gc.strokeLine(x - 5, y + 5, x + 5, y - 5);
			gc.setLineWidth(2);
			gc.setStroke(playerColor(s.kind()));
			gc.strokeLine(x - 5, y - 5, x + 5, y + 5);
			gc.strokeLine(x - 5, y + 5, x + 5, y - 5);
		}
		case WAYPOINT -> { // a pin pointing down at the spot, in the waypoint's own colour
			final double[] xs = { x - 5, x + 5, x };
			final double[] ys = { y - 9, y - 9, y };
			gc.setFill(s.color() == 0 ? Color.WHITE : Color.rgb((s.color() >> 16) & 0xff, (s.color() >> 8) & 0xff, s.color() & 0xff));
			gc.fillPolygon(xs, ys, 3);
			gc.setStroke(OUTLINE);
			gc.strokePolygon(xs, ys, 3);
		}
		case TRANSLOCATOR -> { // a ring: bright when it works, grey when broken
			gc.setStroke(OUTLINE);
			gc.setLineWidth(4);
			gc.strokeOval(x - 5, y - 5, 10, 10);
			gc.setStroke(s.link() != null ? Color.web(TRANSLOCATOR_COLOR) : Color.web("#9aa6a8"));
			gc.setLineWidth(2);
			gc.strokeOval(x - 5, y - 5, 10, 10);
		}
		case ORE -> {
			gc.setFill(Color.web(ORE_COLOR));
			gc.fillRect(x - 3, y - 3, 6, 6);
			gc.setStroke(OUTLINE);
			gc.strokeRect(x - 3, y - 3, 6, 6);
		}
		case TRADER -> { // a triangle pointing up
			final double[] xs = { x - 5, x + 5, x };
			final double[] ys = { y + 4, y + 4, y - 6 };
			gc.setFill(Color.web(TRADER_COLOR));
			gc.fillPolygon(xs, ys, 3);
			gc.setStroke(OUTLINE);
			gc.strokePolygon(xs, ys, 3);
		}
		}
		gc.setLineWidth(1);
	}

	private static void diamond(final GraphicsContext gc, final double x, final double y, final double r, final Color fill,
			final Color stroke) {
		final double[] xs = { x, x + r, x, x - r };
		final double[] ys = { y - r, y, y + r, y };
		gc.setFill(fill);
		gc.fillPolygon(xs, ys, 4);
		gc.setStroke(stroke);
		gc.strokePolygon(xs, ys, 4);
	}

	/** The row's map mark, small, for the table. */
	public Node legend(final Structure s) {
		final Canvas icon = new Canvas(16, 16);
		drawMark(icon.getGraphicsContext2D(), s, 8, s.type() == Type.WAYPOINT ? 12 : 8);
		return icon;
	}

	private void drawTiles(final GraphicsContext gc) {
		if (map == null) {
			return;
		}
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
				final VintageStoryMap from = map;
				if (from == null) {
					return;
				}
				from.loadPieces(minX, minZ, maxX, maxZ, piece -> {
					if (from == map) { // dropped once another map is shown
						loaded.put(((long) piece.tileZ() << X_BITS) | piece.tileX(), piece.pixels());
					}
				});
			} catch (SQLException e) {
				System.err.println("Could not load map tiles: " + e.getMessage());
			}
			Platform.runLater(this::redraw);
		});
	}

	/** Players on top of everything, so the jump marker never hides one. */
	private void drawPlayers(final GraphicsContext gc) {
		if (!showStructures) {
			return;
		}
		final double w = getWidth();
		final double h = getHeight();
		for (final Structure s : structures) {
			if (!s.player()) {
				continue;
			}
			final double x = screenX(s.centerX());
			final double y = screenY(s.centerZ());
			if (x >= -20 && y >= -20 && x <= w + 20 && y <= h + 20) {
				drawMark(gc, s, x, y);
			}
		}
	}

	/** The selected translocator's link, over everything: a bright line and a ring around the other end. */
	private void drawLink(final GraphicsContext gc) {
		if (link == null) {
			return;
		}
		final double ax = screenX(link.centerX()), ay = screenY(link.centerZ());
		final double bx = screenX(link.link()[0] + 0.5), by = screenY(link.link()[2] + 0.5);
		gc.setLineDashes((double[]) null);
		gc.setStroke(OUTLINE);
		gc.setLineWidth(5);
		gc.strokeLine(ax, ay, bx, by);
		gc.setStroke(Color.web(TRANSLOCATOR_COLOR));
		gc.setLineWidth(3);
		gc.strokeLine(ax, ay, bx, by);
		gc.setStroke(Color.WHITE);
		gc.strokeOval(bx - 10, by - 10, 20, 20);
		gc.setStroke(Color.web(TRANSLOCATOR_COLOR));
		gc.setLineWidth(2);
		gc.strokeOval(bx - 13, by - 13, 26, 26);
		gc.setLineWidth(1);
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
