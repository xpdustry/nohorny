// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableWebMvc
@Configuration
public class NoHornyWebConfig implements WebMvcConfigurer {
    @Override
    public void configureMessageConverters(final HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(new MindustryImageConverter());
    }
}
