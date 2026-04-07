// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

public interface LifecycleListener {

    default void onInit() {}

    default void onExit() {}
}
