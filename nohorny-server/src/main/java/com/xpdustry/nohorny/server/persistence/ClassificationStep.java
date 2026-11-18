// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// A classifier run of a [ClassificationRequest], stored in `request_step` at its position in the list.
public record ClassificationStep(String classifier, long durationMillis, Outcome outcome) {}
