package com.praveen.ragassistant.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private SimpleVectorStore vectorStore;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(vectorStore);
        ReflectionTestUtils.setField(ingestionService, "chunkSize", 300);
        ReflectionTestUtils.setField(ingestionService, "chunkOverlap", 50);
        ReflectionTestUtils.setField(ingestionService, "vectorStorePath", "target/test-vector-store.json");
    }

    @Test
    @DisplayName("Should split long text into overlapping chunks respecting chunk size")
    void shouldSplitTextIntoOverlappingChunks() {
        String sampleText = "Acme Corporation offers comprehensive benefits to all regular employees. "
                + "Employees accrue 18 days of paid annual leave per calendar year. "
                + "Annual leave must be applied for at least 7 business days in advance. "
                + "Employees may carry forward a maximum of 30 unused annual leave days. "
                + "Casual leave of 12 days is also provided each year for personal emergencies.";

        List<String> chunks = ingestionService.splitTextIntoChunks(sampleText, 120, 30);

        assertNotNull(chunks);
        assertTrue(chunks.size() > 1, "Text longer than chunkSize should be split into multiple chunks");

        for (String chunk : chunks) {
            assertFalse(chunk.isBlank(), "Chunks must not be blank");
            assertTrue(chunk.length() <= 150, "Chunk should stay within reasonable bounds of specified size");
        }
    }

    @Test
    @DisplayName("Should parse multi-page PDF and attach document name, page number, and version metadata")
    void shouldIngestPdfAndAttachMetadata() throws IOException {
        // Create an in-memory 2-page PDF using Apache PDFBox
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page1 = new PDPage();
            document.addPage(page1);
            try (PDPageContentStream stream = new PDPageContentStream(document, page1)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Page 1: Acme HR Annual leave is 18 days per year.");
                stream.endText();
            }

            PDPage page2 = new PDPage();
            document.addPage(page2);
            try (PDPageContentStream stream = new PDPageContentStream(document, page2)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Page 2: Acme HR Sick leave requires a doctor certificate after 3 days.");
                stream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            pdfBytes = baos.toByteArray();
        }

        // Execute ingestion
        IngestionService.IngestionResult result = ingestionService.ingestPdf(pdfBytes, "hr-policy.pdf", 1);

        assertEquals(2, result.pageCount(), "Should detect exactly 2 pages in PDF");
        assertTrue(result.chunkCount() >= 2, "Should have created at least one chunk per page");

        // Verify chunks were added to vectorStore with proper metadata
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeastOnce()).add(captor.capture());

        List<Document> addedDocuments = captor.getValue();
        assertFalse(addedDocuments.isEmpty());

        Document firstChunk = addedDocuments.get(0);
        assertEquals("hr-policy.pdf", firstChunk.getMetadata().get("document"));
        assertEquals(1, firstChunk.getMetadata().get("version"));
        assertNotNull(firstChunk.getMetadata().get("page"));
    }
}
