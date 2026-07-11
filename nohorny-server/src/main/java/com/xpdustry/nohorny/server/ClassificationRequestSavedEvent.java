// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

/** Published after a classification request has been persisted, so live listeners can react. */
public record ClassificationRequestSavedEvent(long id) {}
