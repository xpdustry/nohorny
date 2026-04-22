// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClassifierProperties.class)
public class ClassifierConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "nohorny.classifier", name = "type", havingValue = "vit")
    @EnableConfigurationProperties(ViTClassifierProperties.class)
    static final class ViTConfiguration {

        @Bean
        public ViTClassifier viTClassifier(final RestClient restClient, final ViTClassifierProperties properties) {
            return new ViTClassifier(restClient, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "nohorny.classifier", name = "type", havingValue = "sightengine")
    @EnableConfigurationProperties(SightEngineClassifierProperties.class)
    static final class SightEngineConfiguration {

        @Bean
        public SightEngineClassifier sightEngineClassifier(
                final RestClient restClient,
                final SightEngineClassifierProperties properties,
                final JsonMapper jsonMapper) {
            return new SightEngineClassifier(restClient, properties, jsonMapper);
        }
    }
}
