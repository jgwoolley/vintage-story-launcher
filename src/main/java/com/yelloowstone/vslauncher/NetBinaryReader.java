package com.yelloowstone.vslauncher;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads what .NET's {@code BinaryWriter} wrote (little endian, strings with a 7-bit length prefix) and Vintage Story's
 * {@code TreeAttribute} on top of it: chunk entities and block entities are stored this way inside the chunk protobuf.
 */
final class NetBinaryReader {
    private final ByteBuffer buffer;

    NetBinaryReader(final byte[] data) {
        this.buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    int remaining() {
        return buffer.remaining();
    }

    void skip(final int bytes) {
        if (bytes < 0 || bytes > buffer.remaining()) {
            throw new IllegalArgumentException("truncated data");
        }
        buffer.position(buffer.position() + bytes);
    }

    int u8() {
        return buffer.get() & 0xff;
    }

    int i32() {
        return buffer.getInt();
    }

    long i64() {
        return buffer.getLong();
    }

    double f64() {
        return buffer.getDouble();
    }

    String string() {
        int length = 0;
        int shift = 0;
        int b;
        do {
            b = u8();
            length |= (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0 && shift < 35);
        final byte[] bytes = new byte[length];
        if (length > buffer.remaining()) {
            throw new IllegalArgumentException("truncated string");
        }
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * A {@code TreeAttribute}: entries of (type id byte, key string, value) ending with a 0 byte. Ints, longs, doubles,
     * strings, bools and nested trees are kept; everything else (item stacks, arrays, bytes) is skipped.
     */
    Map<String, Object> tree() {
        final Map<String, Object> tree = new HashMap<>();
        int type;
        while ((type = u8()) != 0) {
            final String key = string();
            final Object value = attribute(type);
            if (value != null) {
                tree.put(key, value);
            }
        }
        return tree;
    }

    /** Attribute ids from {@code TreeAttribute.RegisterDefaultAttributes} (game 1.22). */
    private Object attribute(final int type) {
        switch (type) {
        case 1:
            return i32();
        case 2:
            return i64();
        case 3:
            return f64();
        case 4:
            skip(4);
            return null;
        case 5:
            return string();
        case 6:
            return tree();
        case 7: // item stack: a "null" flag, then class, id, stack size and its attribute tree
            if (u8() == 0) {
                skip(12);
                tree();
            }
            return null;
        case 8:
            skip(buffer.getShort());
            return null;
        case 9:
            return u8() != 0;
        case 10: {
            final int count = i32();
            for (int i = 0; i < count; i++) {
                string();
            }
            return null;
        }
        case 11, 12:
            skip(4 * i32());
            return null;
        case 13, 15:
            skip(8 * i32());
            return null;
        case 14: {
            final int count = i32();
            for (int i = 0; i < count; i++) {
                tree();
            }
            return null;
        }
        case 16:
            skip(i32());
            return null;
        default:
            throw new IllegalArgumentException("unknown attribute type " + type);
        }
    }
}
