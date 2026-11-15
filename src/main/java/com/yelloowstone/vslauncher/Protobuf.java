package com.yelloowstone.vslauncher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Just enough protobuf wire parsing to read the few fields we need from Vintage Story's blobs.
 * Avoids depending on the bundled descriptor, which does not match the real map piece layout.
 */
final class Protobuf {

    /** One field of a message; exactly one of {@code number} (varint) or {@code bytes} (length-delimited) is set. */
    record Field(int id, long number, byte[] bytes) {
        String string() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private Protobuf() {
    }

    static List<Field> parse(final byte[] data) {
        final List<Field> fields = new ArrayList<>();
        final int[] pos = { 0 };
        while (pos[0] < data.length) {
            final long key = varint(data, pos);
            final int id = (int) (key >>> 3);
            switch ((int) (key & 7)) {
            case 0 -> fields.add(new Field(id, varint(data, pos), null));
            case 1 -> pos[0] += 8;
            case 2 -> {
                final int length = (int) varint(data, pos);
                if (length < 0 || pos[0] + length > data.length) {
                    throw new IllegalArgumentException("truncated field " + id);
                }
                fields.add(new Field(id, 0, java.util.Arrays.copyOfRange(data, pos[0], pos[0] + length)));
                pos[0] += length;
            }
            case 5 -> pos[0] += 4;
            case 3 -> skipGroup(data, pos, id);
            case 4 -> {
                return fields; // end of an enclosing group
            }
            default -> throw new IllegalArgumentException("unsupported wire type in field " + id);
            }
        }
        return fields;
    }

    /**
     * The values of one length-delimited (possibly repeated) field, copying nothing else: map regions are hundreds of KB,
     * almost all of it maps we do not need.
     */
    static List<byte[]> repeated(final byte[] data, final int fieldId) {
        final List<byte[]> values = new ArrayList<>();
        final int[] pos = { 0 };
        while (pos[0] < data.length) {
            final long key = varint(data, pos);
            final int id = (int) (key >>> 3);
            final int wire = (int) (key & 7);
            if (wire == 2) {
                final int length = (int) varint(data, pos);
                if (length < 0 || pos[0] + length > data.length) {
                    throw new IllegalArgumentException("truncated field " + id);
                }
                if (id == fieldId) {
                    values.add(java.util.Arrays.copyOfRange(data, pos[0], pos[0] + length));
                }
                pos[0] += length;
            } else if (wire == 4) {
                return values;
            } else {
                skip(data, pos, wire, id);
            }
        }
        return values;
    }

    /**
     * The 64-bit (wire type 1) fields of a message read as little-endian doubles, by field id (the last one wins):
     * protobuf-net writes {@code double}s this way, e.g. {@code Vec3d} x/y/z = fields 1-3. Absent fields were 0.
     */
    static java.util.Map<Integer, Double> doubles(final byte[] data) {
        final java.util.Map<Integer, Double> values = new java.util.HashMap<>();
        final int[] pos = { 0 };
        while (pos[0] < data.length) {
            final long key = varint(data, pos);
            final int id = (int) (key >>> 3);
            final int wire = (int) (key & 7);
            if (wire == 1) {
                if (pos[0] + 8 > data.length) {
                    throw new IllegalArgumentException("truncated field " + id);
                }
                values.put(id, java.nio.ByteBuffer.wrap(data, pos[0], 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).getDouble());
                pos[0] += 8;
            } else if (wire == 4) {
                return values;
            } else {
                skip(data, pos, wire, id);
            }
        }
        return values;
    }

    /** Skips a value of the given wire type (the key already read). */
    private static void skip(final byte[] data, final int[] pos, final int wire, final int id) {
        switch (wire) {
        case 0 -> varint(data, pos);
        case 1 -> pos[0] += 8;
        case 2 -> {
            // not "pos[0] += varint(...)": Java reads pos[0] before varint moves it past the length
            final int length = (int) varint(data, pos);
            pos[0] += length;
        }
        case 3 -> skipGroup(data, pos, id);
        case 5 -> pos[0] += 4;
        default -> throw new IllegalArgumentException("unsupported wire type in field " + id);
        }
        if (pos[0] > data.length) {
            throw new IllegalArgumentException("truncated field " + id);
        }
    }

    /**
     * Skips a group (wire type 3, old protobuf; protobuf-net still writes some nested values this way, e.g. in Vintage
     * Story's map regions) up to its end marker.
     */
    private static void skipGroup(final byte[] data, final int[] pos, final int id) {
        while (pos[0] < data.length) {
            final long key = varint(data, pos);
            final int wire = (int) (key & 7);
            if (wire == 4) {
                return;
            }
            skip(data, pos, wire, (int) (key >>> 3));
        }
        throw new IllegalArgumentException("unterminated group in field " + id);
    }

    static long varint(final byte[] data, final int[] pos) {
        long result = 0;
        int shift = 0;
        while (true) {
            if (pos[0] >= data.length || shift > 63) {
                throw new IllegalArgumentException("bad varint");
            }
            final byte b = data[pos[0]++];
            result |= (long) (b & 0x7f) << shift;
            if (b >= 0) {
                return result;
            }
            shift += 7;
        }
    }
}
