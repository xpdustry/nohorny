// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.BufferedImageHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
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
        return RestClient.builder()
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(new JacksonJsonHttpMessageConverter(mapper))
                        .addCustomConverter(new ResourceHttpMessageConverter()))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void configureMessageConverters(final HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(new BufferedImageHttpMessageConverter());
        builder.addCustomConverter(new MindustryImageConverter());
    }
}
