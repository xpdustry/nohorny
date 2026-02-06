// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.classification;

import com.google.gson.JsonElement;
import com.xpdustry.nohorny.NoHornyListener;
import java.awt.image.BufferedImage;

public interface Classifier extends NoHornyListener {

    String name();

    Thresholds thresholds();

    String version();

    Result classify(final BufferedImage image) throws Exception;

    record Result(Classification classification, JsonElement json) {}
}
