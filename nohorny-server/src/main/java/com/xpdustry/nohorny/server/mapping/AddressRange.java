// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.mapping;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/// A block of IPv4 or IPv6 addresses in CIDR notation, such as `172.16.0.0/12` or `fd00::/8`.
///
/// @param network the first address of the block, its host bits cleared
/// @param prefix the number of leading bits shared by the addresses of the block
public record AddressRange(InetAddress network, int prefix) {

    public AddressRange {
        final var bytes = network.getAddress();
        if (prefix < 0 || prefix > bytes.length * Byte.SIZE) {
            throw new IllegalArgumentException("prefix out of range");
        }
        if (!Arrays.equals(bytes, mask(bytes, prefix))) {
            throw new IllegalArgumentException("host bits are set");
        }
    }

    /// Parses an address range, the host bits are cleared so `172.18.0.1/16` gives `172.18.0.0/16`.
    ///
    /// @param value a range in CIDR notation, or a single address
    /// @throws IllegalArgumentException if the value is not an IP literal with an optional prefix in range
    public static AddressRange parse(final String value) {
        final var trimmed = value.strip();
        final var slash = trimmed.indexOf('/');
        final var address = parseAddress(slash == -1 ? trimmed : trimmed.substring(0, slash));
        if (address == null) {
            throw new IllegalArgumentException("invalid address");
        }
        final var bytes = address.getAddress();
        final int prefix;
        if (slash == -1) {
            prefix = bytes.length * Byte.SIZE;
        } else {
            final var digits = trimmed.substring(slash + 1);
            if (digits.isEmpty() || digits.length() > 3 || !digits.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("invalid prefix");
            }
            prefix = Integer.parseInt(digits);
            if (prefix > bytes.length * Byte.SIZE) {
                throw new IllegalArgumentException("prefix out of range");
            }
        }
        try {
            return new AddressRange(InetAddress.getByAddress(mask(bytes, prefix)), prefix);
        } catch (final UnknownHostException exception) {
            throw new IllegalStateException("An address of a valid length was refused", exception);
        }
    }

    /// Parses an IP literal without any lookup. An IPv4-mapped IPv6 address, like `::ffff:172.18.0.1`, is an IPv4 one.
    ///
    /// @return `null` if the value is not an IP literal, or has a scope
    public static @Nullable InetAddress parseAddress(final String value) {
        if (value.indexOf('%') != -1) {
            return null;
        }
        try {
            return InetAddress.ofLiteral(value);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    /// Parses the remote address of a connection, an IP literal written by the servlet container. The scope of an IPv6
    /// link-local address, like the `%1` of `fe80:0:0:0:0:0:0:1%1`, is dropped, the ranges have none.
    ///
    /// @return `null` if the value is not an IP literal
    public static @Nullable InetAddress parseRemoteAddress(final String value) {
        final var scope = value.indexOf('%');
        return parseAddress(scope == -1 ? value : value.substring(0, scope));
    }

    /// @return whether the address is in this block, always `false` for an address of the other family
    public boolean contains(final InetAddress address) {
        final var bytes = address.getAddress();
        return bytes.length == this.network.getAddress().length
                && Arrays.equals(mask(bytes, this.prefix), this.network.getAddress());
    }

    /// @return the normalized CIDR notation, the IPv6 addresses compressed like `fd00::/8`
    @Override
    public String toString() {
        return format(this.network) + "/" + this.prefix;
    }

    private static byte[] mask(final byte[] bytes, final int prefix) {
        final var masked = bytes.clone();
        for (int i = 0; i < masked.length; i++) {
            final var kept = Math.clamp(prefix - i * Byte.SIZE, 0, Byte.SIZE);
            masked[i] &= (byte) (0xFF << (Byte.SIZE - kept));
        }
        return masked;
    }

    /// Formats IPv6 addresses following RFC 5952: the longest run of two zero groups or more becomes `::`.
    private static String format(final InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return address.getHostAddress();
        }
        final var bytes = address.getAddress();
        final var groups = new int[8];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = ((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF);
        }
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < groups.length; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            var end = i;
            while (end < groups.length && groups[end] == 0) {
                end++;
            }
            if (end - i > bestLength) {
                bestStart = i;
                bestLength = end - i;
            }
            i = end;
        }
        final var builder = new StringBuilder();
        for (int i = 0; i < groups.length; i++) {
            if (i == bestStart) {
                builder.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != ':') {
                builder.append(':');
            }
            builder.append(Integer.toHexString(groups[i]));
        }
        return builder.toString();
    }
}
