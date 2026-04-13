// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

interface LifecycleListener {

    default void onInit() {}

    default void onExit() {}
}
