// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.event;

@FunctionalInterface
public interface EventListener<T> {

    void onEvent(final T event);
}
