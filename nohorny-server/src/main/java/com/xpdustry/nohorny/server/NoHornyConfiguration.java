// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class NoHornyConfiguration {

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
                .configureMessageConverters(
                        converters -> converters.addCustomConverter(new JacksonJsonHttpMessageConverter(mapper)))
                .requestFactory(requestFactory)
                .build();
    }
}
