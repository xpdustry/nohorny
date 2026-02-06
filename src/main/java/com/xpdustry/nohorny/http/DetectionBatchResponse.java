// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.http;

import java.util.Map;

public record DetectionBatchResponse(Map<Integer, DetectionResult> classifications) {}
