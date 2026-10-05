// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/// The SQLite database and the image files. The schema is migrated by Flyway, then mapped by the JPA entities of
/// this package, the images are kept outside the database by the [ImageStore].
///
/// Since every transaction takes the write lock, the repositories do not open transactions by themselves.
/// Reads run in autocommit mode alongside the writer, writes go through the `@Transactional` services.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
@EnableJpaRepositories(enableDefaultTransactions = false)
public class PersistenceConfiguration {

    @Bean
    public HikariDataSource dataSource(final StorageProperties properties) throws IOException {
        final var absolute = properties.database().toAbsolutePath();
        final var parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final var config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5_000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        // Take the write lock when a transaction begins rather than on its first write, avoiding upgrade deadlocks
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        final var sqlite = new SQLiteDataSource(config);
        sqlite.setUrl("jdbc:sqlite:" + absolute);

        // WAL allows concurrent readers alongside the single writer
        final var pool = new HikariDataSource();
        pool.setPoolName("nohorny-sqlite");
        pool.setDataSource(sqlite);
        pool.setMaximumPoolSize(4);
        return pool;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
