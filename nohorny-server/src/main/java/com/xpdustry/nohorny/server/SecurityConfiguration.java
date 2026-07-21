// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.persistence.UserAccountRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    public UserDetailsService userDetailsService(final UserAccountRepository users) {
        return username -> {
            final var account = users.findById(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
            final var builder = User.withUsername(account.getUsername())
                    .password(account.getPasswordHash())
                    .roles("USER");
            if (account.isAdmin()) {
                builder.roles("USER", "ADMIN");
            }
            return builder.build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final ApiSecurityProperties properties) {
        return http.authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/api/status", "/login", "/error").permitAll();
                    if (properties.apiDefaultPolicy() == ApiSecurityProperties.ApiDefaultPolicy.ALLOW_ALL) {
                        authorize.requestMatchers("/api/classify").permitAll();
                    } else {
                        authorize.requestMatchers("/api/classify").authenticated();
                    }
                    authorize.requestMatchers("/admin", "/admin/**").hasRole("ADMIN");
                    authorize.anyRequest().permitAll();
                })
                .httpBasic(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/classify"))
                .build();
    }
}
