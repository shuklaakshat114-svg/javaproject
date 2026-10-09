package com.healthcart.repository;

import com.healthcart.domain.Prescription;
import com.healthcart.domain.PrescriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByIdAndBuyerId(Long id, Long buyerId);

    Page<Prescription> findByBuyerIdOrderByUploadedAtDesc(Long buyerId, Pageable pageable);

    Page<Prescription> findByStatusOrderByUploadedAtAsc(PrescriptionStatus status, Pageable pageable);

    long countByStatus(PrescriptionStatus status);

    Optional<Prescription> findByOrderId(Long orderId);

    Optional<Prescription> findByStorageKey(String storageKey);
}
