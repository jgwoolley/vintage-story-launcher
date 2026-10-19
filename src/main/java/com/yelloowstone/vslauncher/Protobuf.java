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
            default -> throw new IllegalArgumentException("unsupported wire type in field " + id);
            }
        }
        return fields;
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
