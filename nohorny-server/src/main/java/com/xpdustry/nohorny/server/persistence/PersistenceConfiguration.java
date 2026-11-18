// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/// The only code tied to the SQLite driver, the rest only sees the [javax.sql.DataSource] through
/// [com.xpdustry.nohorny.server.database.SQLiteDatabase].
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
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
        // Take the write lock when a transaction begins rather than on its first write, avoiding upgrade deadlocks.
        //   Used by Flyway, SQLiteDatabase begins its write transactions as IMMEDIATE by itself
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        final var sqlite = new SQLiteDataSource(config);
        sqlite.setUrl("jdbc:sqlite:" + absolute);

        // WAL allows concurrent readers alongside the single writer. The connections stay in auto-commit mode, see
        //   SQLiteDatabaseImpl
        final var pool = new HikariDataSource();
        pool.setPoolName("nohorny-sqlite");
        pool.setDataSource(sqlite);
        pool.setMaximumPoolSize(4);
        return pool;
    }
}
