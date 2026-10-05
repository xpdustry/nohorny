// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.server.ratelimit.RateLimitProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
    NoHornyProperties.class,
    RequestProperties.class,
    RateLimitProperties.class,
    StatusProperties.class
})
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

    /// The pages prerendered by nohorny-frontend. Spring serves `index.html` at `/` and `error/404.html` for unknown
    /// pages without a mapping. The request pages share one shell because they render in the browser.
    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        for (final var path : List.of("/privacy", "/admin", "/admin/users")) {
            registry.addViewController(path).setViewName("forward:" + path + "/index.html");
        }
        registry.addViewController("/requests/{id}").setViewName("forward:/request.html");
    }

    /// The bundled assets have content hashes in their names, so they never change.
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
