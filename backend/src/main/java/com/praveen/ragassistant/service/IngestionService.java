package com.praveen.ragassistant.service;

import com.praveen.ragassistant.exception.InvalidFileException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for document ingestion into the RAG pipeline:
 * 1. Reads uploaded PDF files page by page using Apache PDFBox.
 * 2. Splits extracted text on each page into overlapping chunks (configured via chunk-size and chunk-overlap).
 * 3. Attaches page-level metadata (document, page, version) to each chunk.
 * 4. Generates embeddings and stores chunks in SimpleVectorStore.
 * 5. Persists the updated vector store to data/vector-store.json.
 */
@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final SimpleVectorStore vectorStore;

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${rag.vector-store.file-path:data/vector-store.json}")
    private String vectorStorePath;

    public IngestionService(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Ingestion result record containing page and chunk counts.
     */
    public record IngestionResult(int pageCount, int chunkCount) {
    }

    /**
     * Ingests a PDF document by parsing pages, chunking text, attaching metadata,
     * embedding via SimpleVectorStore, and saving to disk.
     */
    public IngestionResult ingestPdf(byte[] pdfBytes, String fileName, int version) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new InvalidFileException("Cannot ingest empty PDF byte array");
        }

        try (PDDocument pdDocument = Loader.loadPDF(pdfBytes)) {
            int pageCount = pdDocument.getNumberOfPages();
            if (pageCount == 0) {
                throw new InvalidFileException("The uploaded PDF contains 0 pages");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            List<Document> allChunks = new ArrayList<>();

            // Process the PDF page by page to preserve exact source page attribution
            for (int pageNum = 1; pageNum <= pageCount; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(pdDocument);

                if (pageText == null || pageText.trim().isEmpty()) {
                    continue;
                }

                // Chunk the text extracted from this specific page
                List<String> chunksForPage = splitTextIntoChunks(pageText.trim(), chunkSize, chunkOverlap);

                for (String chunkContent : chunksForPage) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("document", fileName);
                    metadata.put("page", pageNum);
                    metadata.put("version", version);

                    Document chunkDoc = new Document(chunkContent, metadata);
                    allChunks.add(chunkDoc);
                }
            }

            if (allChunks.isEmpty()) {
                throw new InvalidFileException("No readable text found in the PDF. Scanned images without OCR are not supported.");
            }

            // Generate embeddings and store all chunks into the vector store
            vectorStore.add(allChunks);
            logger.info("Embedded and added {} chunks from '{}' (pages: {}) into vector store",
                    allChunks.size(), fileName, pageCount);

            // Persist the updated vector store to disk
            saveVectorStore();

            return new IngestionResult(pageCount, allChunks.size());

        } catch (IOException e) {
            logger.error("Failed to parse PDF document '{}': {}", fileName, e.getMessage(), e);
            throw new InvalidFileException("Failed to read or parse PDF file: " + e.getMessage(), e);
        }
    }

    /**
     * Helper for splitting arbitrary text into overlapping chunks.
     * Accessible for testing and text-based ingestion.
     */
    public List<String> splitTextIntoChunks(String text, int size, int overlap) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        if (size <= 0) {
            size = 500;
        }
        if (overlap < 0 || overlap >= size) {
            overlap = 50;
        }

        List<String> chunks = new ArrayList<>();
        int textLength = text.length();

        if (textLength <= size) {
            chunks.add(text);
            return chunks;
        }

        int step = size - overlap;
        int start = 0;

        while (start < textLength) {
            int end = Math.min(start + size, textLength);

            // If we're not at the very end of the text, try to find a natural word boundary
            if (end < textLength) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start + (size / 2)) {
                    end = lastSpace;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // Move forward by step
            start += step;
            if (start >= textLength) {
                break;
            }
        }

        return chunks;
    }

    /**
     * Persists the in-memory SimpleVectorStore to the configured JSON file.
     */
    public void saveVectorStore() {
        try {
            File targetFile = new File(vectorStorePath);
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (created) {
                    logger.info("Created directory structure for vector store: {}", parentDir.getAbsolutePath());
                }
            }
            vectorStore.save(targetFile);
            logger.info("Persisted vector store to disk: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to save vector store to {}: {}", vectorStorePath, e.getMessage(), e);
        }
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }
}
