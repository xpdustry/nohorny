// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import java.util.List;

public record ClassifierChain(List<Classifier> classifiers) {

    public ClassifierChain {
        if (classifiers.isEmpty()) {
            throw new IllegalArgumentException("The classifier chain is empty");
        }
    }
}
