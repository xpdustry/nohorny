// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.requests")
@Validated
public record RequestProperties(@Positive int capacity) {}
