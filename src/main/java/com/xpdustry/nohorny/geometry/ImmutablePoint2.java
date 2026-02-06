// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.geometry;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

@JsonAdapter(ImmutablePoint2.TypeAdapter.class)
public record ImmutablePoint2(int x, int y) implements Comparable<ImmutablePoint2> {

    @Override
    public int compareTo(final ImmutablePoint2 o) {
        final var result = Integer.compare(this.x, o.x);
        return result != 0 ? result : Integer.compare(this.y, o.y);
    }

    public static final class TypeAdapter extends com.google.gson.TypeAdapter<ImmutablePoint2> {

        @Override
        public @Nullable ImmutablePoint2 read(final JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            final var value = in.nextString();
            final var separator = value.indexOf(',');
            if (separator < 0 || separator != value.lastIndexOf(',')) {
                throw new IOException("Expected point in the format 'x,y', got: " + value);
            }

            try {
                final var x = Integer.parseInt(value.substring(0, separator));
                final var y = Integer.parseInt(value.substring(separator + 1));
                return new ImmutablePoint2(x, y);
            } catch (final NumberFormatException e) {
                throw new IOException("Expected point in the format 'x,y', got: " + value, e);
            }
        }

        @Override
        public void write(final JsonWriter out, final @Nullable ImmutablePoint2 value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.x() + "," + value.y());
        }
    }
}
