// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.plugin;

interface LifecycleListener {

    default void onInit() {}

    // Called at the end of every frame while a game is running
    default void onTick() {}

    default void onExit() {}
}
