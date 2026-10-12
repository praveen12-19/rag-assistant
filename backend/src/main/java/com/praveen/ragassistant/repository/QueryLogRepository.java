package com.praveen.ragassistant.repository;

import com.praveen.ragassistant.entity.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {

    /**
     * Finds all query logs ordered by creation time descending.
     */
    List<QueryLog> findAllByOrderByCreatedAtDesc();

    /**
     * Finds all unanswered queries (refusals below threshold) ordered by creation time descending.
     */
    List<QueryLog> findByAnsweredFalseOrderByCreatedAtDesc();
}
