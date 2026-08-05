// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;

interface SettingCodec<T> {

    String encode(final T value);

    T decode(final String value);

    SettingCodec<String> OfString = new SettingCodec<>() {
        @Override
        public String encode(final String value) {
            return value;
        }

        @Override
        public String decode(final String value) {
            return value;
        }

        @Override
        public String toString() {
            return "SettingCodec.OfString";
        }
    };

    SettingCodec<String> OfCountryCodeList = new SettingCodec<>() {
        @Override
        public String encode(final String value) {
            return value;
        }

        @Override
        public String decode(final String value) {
            final var countries = new LinkedHashSet<String>();
            for (final var element : value.split(",", -1)) {
                final var country = element.trim().toUpperCase(Locale.ROOT);
                if (country.length() != 2 || country.chars().anyMatch(c -> c < 'A' || c > 'Z')) {
                    throw new IllegalArgumentException("'" + element + "' is not a two letter country code");
                }
                if (!countries.add(country)) {
                    throw new IllegalArgumentException("'" + country + "' appears more than once");
                }
            }
            return String.join(",", countries);
        }

        @Override
        public String toString() {
            return "SettingCodec.OfCountryCodeList";
        }
    };

    SettingCodec<URI> OfURI = new SettingCodec<>() {
        @Override
        public String encode(final URI value) {
            return value.toString();
        }

        @Override
        public URI decode(final String value) {
            return URI.create(value);
        }

        @Override
        public String toString() {
            return "SettingCodec.OfURI";
        }
    };

    SettingCodec<Boolean> OfBoolean = new SettingCodec<>() {
        @Override
        public String encode(final Boolean value) {
            return String.valueOf(value);
        }

        @Override
        public Boolean decode(final String value) {
            return Boolean.valueOf(value);
        }

        @Override
        public String toString() {
            return "SettingCodec.OfBoolean";
        }
    };

    record OfEnum<E extends Enum<E>>(Class<E> type) implements SettingCodec<E> {
        @Override
        public String encode(final E value) {
            return value.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public E decode(final String value) {
            for (final var constant : this.type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("There is no enum entry of type " + this.type + " with name " + value);
        }
    }
}
