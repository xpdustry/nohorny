// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.http;

public record DetectionRequester(String type, String name) {
    public DetectionRequester {
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
