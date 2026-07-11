// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RequestRepository extends JpaRepository<ClassificationRequest, Long> {

    List<ClassificationRequestSummary> findAllByOrderByIdDesc(final Limit limit);

    Optional<ClassificationRequestImage> findImageById(final long id);

    @Transactional
    default ClassificationRequest saveWithinCapacity(final ClassificationRequest request, final int capacity) {
        final var saved = this.save(request);
        this.deleteOverCapacity(capacity);
        return saved;
    }

    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM classification_requests
            WHERE id NOT IN (
                SELECT id FROM classification_requests ORDER BY id DESC LIMIT :capacity
            )
            """)
    void deleteOverCapacity(final @Param("capacity") int capacity);
}
