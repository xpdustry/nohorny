// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.net.http.HttpClient;
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
        return RestClient.builder()
                .configureMessageConverters(
                        converters -> converters.addCustomConverter(new JacksonJsonHttpMessageConverter(mapper)))
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build()))
                .build();
    }
}
