// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.google.gson.JsonElement;
import com.xpdustry.nohorny.common.classification.Rating;
import java.awt.image.BufferedImage;

public interface Classifier {

    String name();

    Result classify(final BufferedImage image) throws Exception;

    record Result(Rating rating, JsonElement json) {}
}
