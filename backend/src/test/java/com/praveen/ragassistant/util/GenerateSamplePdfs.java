package com.praveen.ragassistant.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GenerateSamplePdfs {

    @Test
    void generatePdfsFromSampleDocs() throws IOException {
        Path sampleDocsDir = Path.of("sample-docs");
        if (!Files.exists(sampleDocsDir)) {
            sampleDocsDir = Path.of("backend/sample-docs");
        }
        if (!Files.exists(sampleDocsDir)) {
            return;
        }

        String[] files = {"hr-leave-policy.txt", "it-security-policy.txt", "travel-policy.txt"};

        for (String file : files) {
            Path txtPath = sampleDocsDir.resolve(file);
            if (Files.exists(txtPath)) {
                String content = Files.readString(txtPath);
                String pdfFileName = file.replace(".txt", ".pdf");
                Path pdfPath = sampleDocsDir.resolve(pdfFileName);
                createPdfFromPageFormattedText(content, pdfPath.toFile());
                System.out.println("Generated PDF: " + pdfPath.toAbsolutePath());
            }
        }
    }

    private void createPdfFromPageFormattedText(String text, File targetPdf) throws IOException {
        String[] pageSections = text.split("=== PAGE ");

        try (PDDocument document = new PDDocument()) {
            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            String headerTitle = pageSections.length > 0 ? pageSections[0].trim() : "Company Policy";

            for (int i = 1; i < pageSections.length; i++) {
                String section = pageSections[i];
                int firstNewline = section.indexOf('\n');
                String pageNumberStr = firstNewline > 0 ? section.substring(0, firstNewline).replaceAll("[^0-9]", "") : String.valueOf(i);
                String pageBody = firstNewline > 0 ? section.substring(firstNewline + 1).trim() : section.trim();

                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);

                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(titleFont, 14);
                    stream.newLineAtOffset(50, 740);
                    stream.showText(headerTitle);

                    stream.setFont(titleFont, 11);
                    stream.newLineAtOffset(0, -25);
                    stream.showText("Page " + pageNumberStr);

                    stream.setFont(bodyFont, 10);
                    float leading = 15;
                    stream.setLeading(leading);

                    // Word wrap lines at ~80 characters
                    List<String> wrappedLines = wrapText(pageBody, 85);
                    stream.newLine();
                    for (String line : wrappedLines) {
                        stream.showText(line);
                        stream.newLine();
                    }
                    stream.endText();
                }
            }

            document.save(targetPdf);
        }
    }

    private List<String> wrapText(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        String[] paragraphs = text.split("\n");

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                result.add("");
                continue;
            }
            String[] words = trimmed.split("\\s+");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                if (currentLine.length() + word.length() + 1 > maxChars) {
                    result.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    if (!currentLine.isEmpty()) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                }
            }
            if (!currentLine.isEmpty()) {
                result.add(currentLine.toString());
            }
        }
        return result;
    }
}
