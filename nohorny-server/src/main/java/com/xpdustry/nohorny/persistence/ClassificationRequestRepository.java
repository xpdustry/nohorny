// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassificationRequestRepository extends JpaRepository<ClassificationRequest, Long> {

    List<ClassificationRequestSummary> findAllByOrderByIdDesc(final Limit limit);

    Optional<ClassificationRequestImage> findImageById(final long id);

    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM classification_request
            WHERE id NOT IN (SELECT id FROM classification_request ORDER BY id DESC LIMIT :capacity)""")
    void deleteOverCapacity(final @Param("capacity") int capacity);
}
