package com.healthcart.service;

import com.healthcart.domain.*;
import com.healthcart.repository.PrescriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            FileStorageService fileStorageService,
            AuditService auditService) {
        this.prescriptionRepository = prescriptionRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @Transactional
    public Prescription uploadPrescription(Order order, User buyer, MultipartFile file) {
        FileStorageService.StoredFileResult stored = fileStorageService.storePrescription(file);

        Prescription prescription = new Prescription(
                order,
                buyer,
                stored.storageKey(),
                stored.originalName(),
                stored.detectedContentType()
        );

        Prescription saved = prescriptionRepository.save(prescription);
        auditService.record(buyer.getEmail(), "PRESCRIPTION_UPLOAD", "PRESCRIPTION", saved.getId().toString(),
                "Uploaded prescription for order #" + (order != null ? order.getOrderNumber() : "STANDALONE"));
        return saved;
    }

    @Transactional
    public void reviewPrescription(Long prescriptionId, PrescriptionStatus status, String notes, User reviewer) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));

        prescription.setStatus(status);
        prescription.setReviewNotes(notes);
        prescription.setReviewedBy(reviewer);
        prescription.setReviewedAt(LocalDateTime.now());

        prescriptionRepository.save(prescription);

        auditService.record(reviewer.getEmail(), "PRESCRIPTION_REVIEW", "PRESCRIPTION", prescriptionId.toString(),
                "Prescription " + status + ". Notes: " + notes);
    }

    @Transactional(readOnly = true)
    public Page<Prescription> getPendingReviewQueue(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 50));
        return prescriptionRepository.findByStatusOrderByUploadedAtAsc(PrescriptionStatus.PENDING_REVIEW, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Prescription> getBuyerPrescriptions(Long buyerId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 50));
        return prescriptionRepository.findByBuyerIdOrderByUploadedAtDesc(buyerId, pageable);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@ownership.canViewPrescription(#prescriptionId, authentication)")
    public Path loadPrescriptionFile(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        return fileStorageService.loadPrescription(prescription.getStorageKey());
    }

    @Transactional(readOnly = true)
    public Optional<Prescription> getById(Long id) {
        return prescriptionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return prescriptionRepository.countByStatus(PrescriptionStatus.PENDING_REVIEW);
    }
}
