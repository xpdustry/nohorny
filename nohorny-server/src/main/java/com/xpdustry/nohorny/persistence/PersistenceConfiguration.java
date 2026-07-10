// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import java.io.IOException;
import java.nio.file.Files;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DatabaseProperties.class, RequestProperties.class})
@EntityScan(basePackageClasses = PersistenceConfiguration.class)
@EnableJpaRepositories(basePackageClasses = PersistenceConfiguration.class)
public class PersistenceConfiguration {

    @Bean
    public DataSource dataSource(final DatabaseProperties properties) throws IOException {
        final var absolute = properties.path().toAbsolutePath();
        final var parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final var config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5_000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);

        final var dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + absolute);
        new ResourceDatabasePopulator(new ClassPathResource("database/schema.sql")).execute(dataSource);
        return dataSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
