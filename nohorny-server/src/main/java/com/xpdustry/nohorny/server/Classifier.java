// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.google.gson.JsonElement;
import com.xpdustry.nohorny.common.classification.Rating;
import com.xpdustry.nohorny.common.lifecycle.LifecycleListener;
import java.awt.image.BufferedImage;

public interface Classifier extends LifecycleListener {

    String name();

    Result classify(final BufferedImage image) throws Exception;

    record Result(Rating rating, JsonElement json) {}
}
