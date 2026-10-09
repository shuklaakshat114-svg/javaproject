package com.healthcart.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("File Storage Security & Content-Sniffing Tests")
class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        fileStorageService = new FileStorageService(tempDir.toString());
    }

    @Test
    @DisplayName("Valid JPEG image with correct magic bytes is accepted")
    void testStoreProductImageValidJpeg() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        MockMultipartFile file = new MockMultipartFile("image", "sample.jpg", "image/jpeg", jpegBytes);

        FileStorageService.StoredFileResult result = fileStorageService.storeProductImage(file);
        assertNotNull(result);
        assertTrue(result.storageKey().endsWith(".jpg"));
        assertEquals("image/jpeg", result.detectedContentType());
    }

    @Test
    @DisplayName("Valid PNG image with correct magic bytes is accepted")
    void testStoreProductImageValidPng() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("image", "sample.png", "image/png", pngBytes);

        FileStorageService.StoredFileResult result = fileStorageService.storeProductImage(file);
        assertNotNull(result);
        assertTrue(result.storageKey().endsWith(".png"));
        assertEquals("image/png", result.detectedContentType());
    }

    @Test
    @DisplayName("Valid PDF prescription document with correct magic bytes is accepted")
    void testStorePrescriptionValidPdf() {
        byte[] pdfBytes = "%PDF-1.4 sample content".getBytes();
        MockMultipartFile file = new MockMultipartFile("rx", "prescription.pdf", "application/pdf", pdfBytes);

        FileStorageService.StoredFileResult result = fileStorageService.storePrescription(file);
        assertNotNull(result);
        assertTrue(result.storageKey().endsWith(".pdf"));
        assertEquals("application/pdf", result.detectedContentType());
    }

    @Test
    @DisplayName("Disguised executable (.exe renamed to .jpg) is rejected via magic byte sniffing")
    void testDisguisedExeRejected() {
        // Windows PE executable header: MZ (0x4D 0x5A)
        byte[] exeBytes = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile deceptiveFile = new MockMultipartFile("image", "harmless.jpg", "image/jpeg", exeBytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storeProductImage(deceptiveFile));
        assertTrue(ex.getMessage().contains("Invalid file format"));
    }

    @Test
    @DisplayName("Empty or zero-byte file is rejected")
    void testEmptyFileRejected() {
        MockMultipartFile emptyFile = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storeProductImage(emptyFile));
        assertTrue(ex.getMessage().contains("cannot be empty"));
    }

    @Test
    @DisplayName("Oversized image (>2MB) is rejected")
    void testOversizedImageRejected() {
        byte[] largeBytes = new byte[2 * 1024 * 1024 + 10]; // Slightly over 2MB
        largeBytes[0] = (byte) 0xFF;
        largeBytes[1] = (byte) 0xD8;
        largeBytes[2] = (byte) 0xFF;
        MockMultipartFile largeFile = new MockMultipartFile("image", "large.jpg", "image/jpeg", largeBytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storeProductImage(largeFile));
        assertTrue(ex.getMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    @DisplayName("Path traversal load attempts are contained and prevented")
    void testPathTraversalLoadGuarded() {
        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.loadProductImage("../../../../windows/system32/cmd.exe"));
    }
}
