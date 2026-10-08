// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/// Orders the plugin versions the Maven way, the oldest first, so `4.0.0-beta.10-SNAPSHOT` comes after
/// `4.0.0-beta.9` and before `4.0.0-beta.10`, which comes before `4.0.0`.
final class VersionComparator implements Comparator<String> {

    static final VersionComparator INSTANCE = new VersionComparator();

    private static final Pattern TOKEN = Pattern.compile("\\d+|[a-zA-Z]+");
    // The known qualifiers, the oldest first. The others come after a release, alphabetically
    private static final List<String> QUALIFIERS = List.of("beta", "rc", "snapshot", "");

    private VersionComparator() {}

    @Override
    public int compare(final String a, final String b) {
        final var left = tokens(a);
        final var right = tokens(b);
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            final var result = compareTokens(token(left, i, right), token(right, i, left));
            if (result != 0) {
                return result;
            }
        }
        return a.compareTo(b);
    }

    private static List<Object> tokens(final String version) {
        final var tokens = new ArrayList<Object>();
        TOKEN.matcher(version).results().forEach(match -> {
            final var token = match.group();
            tokens.add(Character.isDigit(token.charAt(0)) ? new BigInteger(token) : token.toLowerCase(Locale.ROOT));
        });
        return tokens;
    }

    /// A missing token is a zero next to a number and a release next to a qualifier, so `4.0` equals `4.0.0`
    /// and `4.0.0` comes after `4.0.0-beta.1`.
    private static Object token(final List<Object> tokens, final int index, final List<Object> other) {
        if (index < tokens.size()) {
            return tokens.get(index);
        }
        return other.get(index) instanceof BigInteger ? BigInteger.ZERO : "";
    }

    private static int compareTokens(final Object a, final Object b) {
        if (a instanceof BigInteger left && b instanceof BigInteger right) {
            return left.compareTo(right);
        }
        // A number comes after a qualifier, like 4.0.0.1 after 4.0.0-beta
        if (a instanceof BigInteger) {
            return 1;
        }
        if (b instanceof BigInteger) {
            return -1;
        }
        final var left = QUALIFIERS.indexOf((String) a);
        final var right = QUALIFIERS.indexOf((String) b);
        if (left >= 0 && right >= 0) {
            return Integer.compare(left, right);
        }
        if (left >= 0 || right >= 0) {
            return left >= 0 ? -1 : 1;
        }
        return ((String) a).compareTo((String) b);
    }
}
