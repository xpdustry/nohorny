// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.server.database.SQLiteDatabase;
import com.xpdustry.nohorny.server.mapping.AddressRange;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class RequesterMappingRepository {

    private final SQLiteDatabase database;

    public RequesterMappingRepository(final SQLiteDatabase database) {
        this.database = database;
    }

    public List<RequesterMapping> findAll() {
        return this.database.withReadTransaction(
                transaction -> transaction.prepareStatement("""
                        SELECT id, address_range, requester_type, requester_name, created_at
                        FROM requester_mapping
                        """).executeSelect(RequesterMappingRepository::map));
    }

    public Optional<RequesterMapping> findById(final String id) {
        return this.database.withReadTransaction(transaction ->
                transaction.prepareStatement("""
                        SELECT id, address_range, requester_type, requester_name, created_at
                        FROM requester_mapping
                        WHERE id = ?
                        """).bind(id).executeSingleSelect(RequesterMappingRepository::map));
    }

    public boolean existsByRange(final AddressRange range) {
        return this.database.withReadTransaction(transaction -> transaction
                .prepareStatement("SELECT 1 FROM requester_mapping WHERE address_range = ?")
                .bind(range.toString())
                .executeSingleSelect(_ -> Boolean.TRUE)
                .orElse(false));
    }

    public void insert(final RequesterMapping mapping) {
        this.database.withTransaction(transaction -> transaction
                .prepareStatement("""
                        INSERT INTO requester_mapping (id, address_range, requester_type, requester_name, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .bind(mapping.id())
                .bind(mapping.range().toString())
                .bind(mapping.requester().type())
                .bind(mapping.requester().name())
                .bind(mapping.createdAt())
                .executeSingleUpdate());
    }

    /// Writes the range and the requester, the creation time never changes.
    public void update(final RequesterMapping mapping) {
        this.database.withTransaction(transaction -> transaction
                .prepareStatement("""
                        UPDATE requester_mapping SET address_range = ?, requester_type = ?, requester_name = ?
                        WHERE id = ?
                        """)
                .bind(mapping.range().toString())
                .bind(mapping.requester().type())
                .bind(mapping.requester().name())
                .bind(mapping.id())
                .executeSingleUpdate());
    }

    public void delete(final String id) {
        this.database.withTransaction(transaction -> transaction
                .prepareStatement("DELETE FROM requester_mapping WHERE id = ?")
                .bind(id)
                .executeSingleUpdate());
    }

    public int deleteByUser(final String username) {
        return this.database.withTransaction(transaction -> transaction
                .prepareStatement("DELETE FROM requester_mapping WHERE requester_type = 'USER' AND requester_name = ?")
                .bind(username)
                .executeUpdate());
    }

    private static RequesterMapping map(final ResultSet result) throws SQLException {
        return new RequesterMapping(
                result.getString("id"),
                AddressRange.parse(result.getString("address_range")),
                new Requester(
                        RequesterType.valueOf(result.getString("requester_type")), result.getString("requester_name")),
                Instant.ofEpochMilli(result.getLong("created_at")));
    }
}
