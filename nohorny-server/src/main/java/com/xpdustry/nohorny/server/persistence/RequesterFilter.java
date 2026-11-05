// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// @param name [ValueFilter.None] for the requesters without a name, like the listed servers without a network
public record RequesterFilter(RequesterType type, ValueFilter name) {}
