// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import java.nio.file.Path;

public interface ViTModelSource {

    String name();

    Path retrieve();
}
