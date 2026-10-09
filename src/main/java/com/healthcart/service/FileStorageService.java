package com.healthcart.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path productImagesDir;
    private final Path prescriptionsDir;

    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final long MAX_PRESCRIPTION_SIZE = 5 * 1024 * 1024; // 5MB

    public FileStorageService(@Value("${app.storage.root:./uploads}") String storageRoot) {
        this.productImagesDir = Paths.get(storageRoot, "products").toAbsolutePath().normalize();
        this.prescriptionsDir = Paths.get(storageRoot, "prescriptions").toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.productImagesDir);
            Files.createDirectories(this.prescriptionsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize upload storage directories", e);
        }
    }

    public record StoredFileResult(String storageKey, String originalName, String detectedContentType, long size) {}

    public StoredFileResult storeProductImage(MultipartFile file) {
        validateFileNotEmpty(file);
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Product image exceeds maximum allowed size of 2MB");
        }

        String detectedType = detectFileType(file);
        if (!isAllowedImageType(detectedType)) {
            throw new IllegalArgumentException("Invalid file format. Only JPEG, PNG, and WebP images are allowed.");
        }

        String extension = getExtensionForType(detectedType);
        String storageKey = UUID.randomUUID().toString() + extension;
        Path targetPath = this.productImagesDir.resolve(storageKey).normalize();

        // Path traversal guard
        if (!targetPath.startsWith(this.productImagesDir)) {
            throw new SecurityException("Invalid target file path traversal detected");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFileResult(storageKey, file.getOriginalFilename(), detectedType, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store product image", e);
        }
    }

    public StoredFileResult storePrescription(MultipartFile file) {
        validateFileNotEmpty(file);
        if (file.getSize() > MAX_PRESCRIPTION_SIZE) {
            throw new IllegalArgumentException("Prescription document exceeds maximum allowed size of 5MB");
        }

        String detectedType = detectFileType(file);
        if (!isAllowedPrescriptionType(detectedType)) {
            throw new IllegalArgumentException("Invalid prescription file format. Only PDF, JPEG, and PNG files are allowed.");
        }

        String extension = getExtensionForType(detectedType);
        String storageKey = UUID.randomUUID().toString() + extension;
        Path targetPath = this.prescriptionsDir.resolve(storageKey).normalize();

        // Path traversal guard
        if (!targetPath.startsWith(this.prescriptionsDir)) {
            throw new SecurityException("Invalid target file path traversal detected");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFileResult(storageKey, file.getOriginalFilename(), detectedType, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store prescription document", e);
        }
    }

    public Path loadProductImage(String storageKey) {
        // Sanitize storage key against traversal
        String safeKey = Paths.get(storageKey).getFileName().toString();
        Path file = this.productImagesDir.resolve(safeKey).normalize();
        if (!Files.exists(file) || !file.startsWith(this.productImagesDir)) {
            throw new IllegalArgumentException("Requested image does not exist");
        }
        return file;
    }

    public Path loadPrescription(String storageKey) {
        // Sanitize storage key against traversal
        String safeKey = Paths.get(storageKey).getFileName().toString();
        Path file = this.prescriptionsDir.resolve(safeKey).normalize();
        if (!Files.exists(file) || !file.startsWith(this.prescriptionsDir)) {
            throw new IllegalArgumentException("Requested prescription document does not exist");
        }
        return file;
    }

    private void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
    }

    /**
     * Inspects magic bytes (content sniffing) to determine true file type,
     * ignoring deceptive filenames or client headers.
     */
    public String detectFileType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[16];
            int read = is.read(header);
            if (read < 4) {
                return "application/octet-stream";
            }

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }

            // PNG: 89 50 4E 47 (0x89 'P' 'N' 'G')
            if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "image/png";
            }

            // PDF: 25 50 44 46 ('%' 'P' 'D' 'F')
            if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
                return "application/pdf";
            }

            // WebP: 'RIFF' .... 'WEBP'
            if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46) {
                if (header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
                    return "image/webp";
                }
            }

            return "application/octet-stream";
        } catch (IOException e) {
            log.error("Failed to sniff file magic bytes", e);
            return "application/octet-stream";
        }
    }

    private boolean isAllowedImageType(String contentType) {
        return "image/jpeg".equals(contentType) || "image/png".equals(contentType) || "image/webp".equals(contentType);
    }

    private boolean isAllowedPrescriptionType(String contentType) {
        return "application/pdf".equals(contentType) || "image/jpeg".equals(contentType) || "image/png".equals(contentType);
    }

    private String getExtensionForType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }
}
