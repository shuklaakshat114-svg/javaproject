package com.healthcart.web.controller;

import com.healthcart.domain.Prescription;
import com.healthcart.service.FileStorageService;
import com.healthcart.service.PrescriptionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Controller
public class FileController {

    private final FileStorageService fileStorageService;
    private final PrescriptionService prescriptionService;

    public FileController(FileStorageService fileStorageService, PrescriptionService prescriptionService) {
        this.fileStorageService = fileStorageService;
        this.prescriptionService = prescriptionService;
    }

    @GetMapping("/files/products/{storageKey}")
    @ResponseBody
    public ResponseEntity<Resource> serveProductImage(@PathVariable String storageKey) throws IOException {
        Path filePath = fileStorageService.loadProductImage(storageKey);
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + TimeUnit.DAYS.toSeconds(30))
                .body(resource);
    }

    @GetMapping("/files/prescriptions/{id}")
    @PreAuthorize("@ownership.canViewPrescription(#id, authentication)")
    @ResponseBody
    public ResponseEntity<Resource> servePrescription(@PathVariable Long id) throws IOException {
        Prescription prescription = prescriptionService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));

        Path filePath = fileStorageService.loadPrescription(prescription.getStorageKey());
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = prescription.getContentType();
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + prescription.getOriginalFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .body(resource);
    }
}
