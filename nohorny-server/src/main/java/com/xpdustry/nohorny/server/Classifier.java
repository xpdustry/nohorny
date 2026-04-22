// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.Rating;
import java.awt.image.BufferedImage;

public interface Classifier {

    String name();

    Result classify(final BufferedImage image) throws Exception;

    record Result(Rating rating, String metadata) {}
}
