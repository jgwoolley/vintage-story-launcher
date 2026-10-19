package com.yelloowstone.vslauncher;

import java.util.Locale;

/**
 * Coordinates as Vintage Story writes them: a plain number is relative to the spawn (the middle of
 * the world by default), a number starting with "=" is absolute.
 */
public class MapCoordinates {
    /** Middle of a default size (1,024,000 block) world. */
    public static final int[] DEFAULT_ORIGIN = { 512000, 512000 };

    private final int originX;
    private final int originZ;

    public MapCoordinates(final int[] origin) {
        this.originX = origin[0];
        this.originZ = origin[1];
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginZ() {
        return originZ;
    }

    /**
     * Parses "x z" (or "x y z", ignoring y) separated by spaces, commas or slashes, e.g.
     * {@code =512100 =511900} (absolute), {@code 100 -250} (relative to spawn) or a mix.
     *
     * @return absolute {x, z}
     * @throws IllegalArgumentException with a message fit to show the user
     */
    public double[] parse(final String text) {
        // Tolerate pasted forms such as "(=512100, =110, =511900)" or "[100 64 -250]"
        final String[] tokens = text == null ? new String[0] : text.replaceAll("[()\\[\\]{}]", " ").strip().split("[\\s,/;]+");
        if (text == null || text.isBlank() || (tokens.length != 2 && tokens.length != 3)) {
            throw new IllegalArgumentException("Enter x and z, e.g. 100 -250 (from spawn) or =512100 =511900 (absolute)");
        }
        final String xToken = tokens[0];
        final String zToken = tokens[tokens.length - 1]; // with three values the middle one is y
        return new double[] { value(xToken, originX), value(zToken, originZ) };
    }

    private static double value(final String token, final int origin) {
        final boolean absolute = token.startsWith("=");
        final String number = absolute ? token.substring(1) : token;
        try {
            final double parsed = Double.parseDouble(number);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new NumberFormatException();
            }
            return absolute ? parsed : origin + parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + token + "' is not a number");
        }
    }

    /** "=512100 (100)" style text for an absolute coordinate pair. */
    public String describe(final double x, final double z) {
        return String.format(Locale.ROOT, "x =%d (%+d)  z =%d (%+d)", Math.round(x), Math.round(x - originX),
                Math.round(z), Math.round(z - originZ));
    }
}
