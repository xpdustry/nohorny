// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.security;

public final class Usernames {

    public static final String PATTERN = "[a-z0-9_.-]{3,32}";
    public static final String MESSAGE =
            "username must be 3 to 32 characters among lowercase letters, digits, '_', '.' and '-'";

    private Usernames() {}
}
