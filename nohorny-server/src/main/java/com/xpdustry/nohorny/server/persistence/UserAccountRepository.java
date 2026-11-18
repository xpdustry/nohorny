// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.server.database.SQLiteDatabase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class UserAccountRepository {

    private final SQLiteDatabase database;

    public UserAccountRepository(final SQLiteDatabase database) {
        this.database = database;
    }

    public Optional<UserAccount> findById(final String username) {
        return this.database.withReadTransaction(transaction ->
                transaction.prepareStatement("""
                        SELECT username, password_hash, admin, rate_limit, session_version, created_at
                        FROM user_account
                        WHERE username = ?
                        """).bind(username).executeSingleSelect(UserAccountRepository::map));
    }

    public boolean existsById(final String username) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("SELECT 1 FROM user_account WHERE username = ?")
                .bind(username)
                .executeSingleSelect(_ -> Boolean.TRUE)
                .orElse(false));
    }

    public List<UserAccount> findAllOrderedByUsername() {
        return this.database.withReadTransaction(
                transaction -> transaction.prepareStatement("""
                        SELECT username, password_hash, admin, rate_limit, session_version, created_at
                        FROM user_account
                        ORDER BY username
                        """).executeSelect(UserAccountRepository::map));
    }

    public long countAdministrators() {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("SELECT COUNT(*) FROM user_account WHERE admin = 1")
                .executeSelect(result -> result.getLong(1))
                .getFirst());
    }

    public void insert(final UserAccount account) {
        this.database.withTransaction(transaction -> transaction
                .prepareStatement("""
                        INSERT INTO user_account
                            (username, password_hash, admin, rate_limit, session_version, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .bind(account.username())
                .bind(account.passwordHash())
                .bind(account.admin())
                .bind(account.rateLimit())
                .bind(account.sessionVersion())
                .bind(account.createdAt())
                .executeSingleUpdate());
    }

    /// Writes everything but the username and the creation time, which never change.
    public void update(final UserAccount account) {
        this.database.withTransaction(transaction -> transaction
                .prepareStatement("""
                        UPDATE user_account
                        SET password_hash = ?, admin = ?, rate_limit = ?, session_version = ?
                        WHERE username = ?
                        """)
                .bind(account.passwordHash())
                .bind(account.admin())
                .bind(account.rateLimit())
                .bind(account.sessionVersion())
                .bind(account.username())
                .executeSingleUpdate());
    }

    public void delete(final String username) {
        this.database.withTransaction(transaction -> transaction
                .prepareStatement("DELETE FROM user_account WHERE username = ?")
                .bind(username)
                .executeSingleUpdate());
    }

    private static UserAccount map(final ResultSet result) throws SQLException {
        return new UserAccount(
                result.getString("username"),
                result.getString("password_hash"),
                result.getBoolean("admin"),
                result.getInt("rate_limit"),
                result.getLong("session_version"),
                Instant.ofEpochMilli(result.getLong("created_at")));
    }
}
