// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({NoHornyProperties.class, RequestProperties.class, StatusProperties.class})
public class NoHornyConfiguration implements WebMvcConfigurer {

    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.shared();
    }

    @Bean
    public RestClient restClient(final JsonMapper mapper) {
        final var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        final var jsonConverter = new JacksonJsonHttpMessageConverter(mapper);
        // GitHub raw endpoints serve json files as text/plain
        jsonConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        return RestClient.builder()
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(jsonConverter)
                        .addCustomConverter(new ResourceHttpMessageConverter()))
                .requestFactory(requestFactory)
                .build();
    }

    /// The static pages behind clean paths, `/` serves `index.html` on its own.
    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        registry.addViewController("/requests/{id}").setViewName("forward:/request.html");
        registry.addViewController("/admin").setViewName("forward:/admin.html");
    }
}
