// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import java.nio.file.Path;

public record LocalViTModelSource(LocalViTModelSourceProperties properties) implements ViTModelSource {

    @Override
    public String name() {
        return "local/" + this.properties.path().getFileName().toString();
    }

    @Override
    public Path retrieve() {
        return this.properties.path();
    }
}
