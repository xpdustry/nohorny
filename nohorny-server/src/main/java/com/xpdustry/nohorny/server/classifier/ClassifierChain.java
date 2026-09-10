// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import java.util.List;

/// The ordered classifiers used to classify images. An image advances through the chain while the
/// latest classifier rates it NSFW.
public record ClassifierChain(List<Classifier> classifiers) {}
