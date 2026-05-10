// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

public record ClassificationResponse(String classifier, Rating rating, double confidence, String identifier) {}
