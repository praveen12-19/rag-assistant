package com.praveen.ragassistant.service;

import com.praveen.ragassistant.dto.DocumentResponse;
import com.praveen.ragassistant.entity.DocumentEntity;
import com.praveen.ragassistant.exception.InvalidFileException;
import com.praveen.ragassistant.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service orchestrating PDF document validation, versioning, persistence in MySQL,
 * and vector ingestion.
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;

    public DocumentService(DocumentRepository documentRepository, IngestionService ingestionService) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    /**
     * Validates an uploaded PDF, determines the document version, ingests pages and chunks
     * into the vector store, and stores document metadata in MySQL.
     */
    @Transactional
    public DocumentResponse uploadAndIngest(MultipartFile file) {
        // 1. Validation: file must not be null or empty
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file cannot be null or empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileException("Uploaded file must have a valid filename");
        }

        // Clean filename (strip directory path separators if any)
        String cleanFileName = originalFilename.trim();
        int lastSlash = Math.max(cleanFileName.lastIndexOf('/'), cleanFileName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            cleanFileName = cleanFileName.substring(lastSlash + 1);
        }

        // 2. Validation: must be a PDF file
        if (!cleanFileName.toLowerCase().endsWith(".pdf")) {
            throw new InvalidFileException("Invalid file format. Only PDF files are supported (.pdf)");
        }

        // Optional MIME type check if present
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")
                && !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new InvalidFileException("Invalid content type '" + contentType + "'. Expected 'application/pdf'");
        }

        // 3. Determine version: auto-increment if the same filename has been uploaded previously
        Optional<DocumentEntity> existing = documentRepository.findTopByFileNameOrderByVersionDesc(cleanFileName);
        int version = existing.map(doc -> doc.getVersion() + 1).orElse(1);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidFileException("Failed to read file bytes: " + e.getMessage(), e);
        }

        // 4. Ingest PDF page by page into the vector store
        IngestionService.IngestionResult result = ingestionService.ingestPdf(bytes, cleanFileName, version);

        // 5. Save metadata record to MySQL
        DocumentEntity entity = new DocumentEntity(
                cleanFileName,
                version,
                result.pageCount(),
                result.chunkCount(),
                LocalDateTime.now()
        );
        DocumentEntity saved = documentRepository.save(entity);
        logger.info("Saved document metadata to MySQL: id={}, name='{}', v={}, pages={}, chunks={}",
                saved.getId(), saved.getFileName(), saved.getVersion(), saved.getPageCount(), saved.getChunkCount());

        return toResponse(saved);
    }

    /**
     * Lists all uploaded documents from MySQL.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private DocumentResponse toResponse(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getFileName(),
                entity.getVersion(),
                entity.getPageCount(),
                entity.getChunkCount(),
                entity.getUploadedAt()
        );
    }
}
