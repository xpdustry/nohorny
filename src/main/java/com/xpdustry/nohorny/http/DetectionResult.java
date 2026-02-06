// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.http;

import com.xpdustry.nohorny.classification.Classification;

public record DetectionResult(String classifier, String version, Classification classification) {}
