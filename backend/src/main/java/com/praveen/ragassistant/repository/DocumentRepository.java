package com.praveen.ragassistant.repository;

import com.praveen.ragassistant.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    /**
     * Finds the latest version of a document by file name.
     */
    Optional<DocumentEntity> findTopByFileNameOrderByVersionDesc(String fileName);

    /**
     * Lists all uploaded documents ordered by upload timestamp descending.
     */
    List<DocumentEntity> findAllByOrderByUploadedAtDesc();
}
