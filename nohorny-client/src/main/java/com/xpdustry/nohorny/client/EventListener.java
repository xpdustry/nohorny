// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

@FunctionalInterface
interface EventListener<T> {

    void onEvent(final T event);
}
