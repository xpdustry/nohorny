// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface RequesterMappingRepository extends ListCrudRepository<RequesterMapping, String> {

    boolean existsByRange(String range);

    /// Native, so the rows are read as stored rather than served from the persistence context of a transaction that
    /// loaded them before a change.
    @Query(nativeQuery = true, value = """
            SELECT id, address_range AS range, requester_type AS requesterType, requester_name AS requesterName
            FROM requester_mapping
            """)
    List<Row> findRows();

    /// @return the number of deleted mappings
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RequesterMapping m WHERE m.requesterType = USER AND m.requesterName = :username")
    int deleteByUser(String username);

    /// A mapping as stored, see [#findRows].
    interface Row {

        String getId();

        String getRange();

        String getRequesterType();

        @Nullable String getRequesterName();
    }
}
