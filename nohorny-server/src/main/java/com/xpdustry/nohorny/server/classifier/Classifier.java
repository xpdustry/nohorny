// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import com.xpdustry.nohorny.common.Rating;
import java.awt.image.BufferedImage;

public interface Classifier {

    String name();

    Result classify(final BufferedImage image) throws Exception;

    record Result(Rating rating, double confidence, String metadata) {}
}
