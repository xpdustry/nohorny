// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.lifecycle;

public interface LifecycleListener {

    default void onInit() {}

    default void onExit() {}
}
