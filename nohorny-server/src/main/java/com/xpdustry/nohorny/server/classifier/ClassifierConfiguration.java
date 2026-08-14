// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClassifierConfiguration.ClassifierProperties.class)
public final class ClassifierConfiguration {

    @ConfigurationProperties("nohorny.classifier")
    @Validated
    public record ClassifierProperties(
            @DefaultValue("vit") @NotEmpty List<ClassifierType> type) {

        public enum ClassifierType {
            VIT,
            SIGHT_ENGINE,
        }
    }

    @Bean
    public ClassifierChain classifierChain(
            final ClassifierProperties properties,
            final ObjectProvider<ViTClassifier> vit,
            final ObjectProvider<SightEngineClassifier> sightEngine) {
        return new ClassifierChain(properties.type().stream()
                .map(type -> switch (type) {
                    case VIT -> vit.getObject();
                    case SIGHT_ENGINE -> sightEngine.getObject();
                })
                .toList());
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(ViTEnabledCondition.class)
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
    @Conditional(SightEngineEnabledCondition.class)
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

    private abstract static class ClassifierEnabledCondition implements Condition {

        private final ClassifierProperties.ClassifierType type;

        private ClassifierEnabledCondition(final ClassifierProperties.ClassifierType type) {
            this.type = type;
        }

        @Override
        public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
            return Binder.get(context.getEnvironment())
                    .bind("nohorny.classifier.type", Bindable.listOf(ClassifierProperties.ClassifierType.class))
                    .orElseGet(() -> List.of(ClassifierProperties.ClassifierType.VIT))
                    .contains(this.type);
        }
    }

    private static final class ViTEnabledCondition extends ClassifierEnabledCondition {

        private ViTEnabledCondition() {
            super(ClassifierProperties.ClassifierType.VIT);
        }
    }

    private static final class SightEngineEnabledCondition extends ClassifierEnabledCondition {

        private SightEngineEnabledCondition() {
            super(ClassifierProperties.ClassifierType.SIGHT_ENGINE);
        }
    }
}
