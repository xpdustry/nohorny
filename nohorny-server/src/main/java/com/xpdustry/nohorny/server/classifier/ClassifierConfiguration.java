// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClassifierConfiguration.ClassifierProperties.class)
public final class ClassifierConfiguration {

    // Dummy prop to allow polymorphic configs
    @ConfigurationProperties("nohorny.classifier")
    @Validated
    public record ClassifierProperties(@NotNull ClassifierType type) {

        public enum ClassifierType {
            VIT,
            SIGHT_ENGINE,
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "nohorny.classifier.type", havingValue = "vit")
    @EnableConfigurationProperties({ViTClassifierProperties.class, ViTConfiguration.ViTSourceProperties.class})
    static final class ViTConfiguration {

        // Dummy prop to allow polymorphic configs
        @ConfigurationProperties("nohorny.classifier.vit.source")
        @Validated
        public record ViTSourceProperties(@NotNull VitSourceType type) {

            public enum VitSourceType {
                LOCAL,
                HUGGING_FACE,
            }
        }

        @Bean
        public ViTClassifier viTClassifier(final ViTClassifierProperties properties, final ViTModelSource source) {
            return new ViTClassifier(properties, source);
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnProperty(name = "nohorny.classifier.vit.source.type", havingValue = "local")
        @EnableConfigurationProperties(LocalViTModelSourceProperties.class)
        static final class LocalSourceConfiguration {

            @Bean
            public ViTModelSource localViTModelSource(final LocalViTModelSourceProperties properties) {
                return new LocalViTModelSource(properties);
            }
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnProperty(name = "nohorny.classifier.vit.source.type", havingValue = "hugging-face")
        @EnableConfigurationProperties(HuggingFaceViTModelSourceProperties.class)
        static final class HuggingFaceSourceConfiguration {

            @Bean
            public ViTModelSource huggingFaceViTModelSource(
                    final HuggingFaceViTModelSourceProperties properties, final RestClient restClient) {
                return new HuggingFaceViTModelSource(properties, restClient);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "nohorny.classifier.type", havingValue = "sight-engine")
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
