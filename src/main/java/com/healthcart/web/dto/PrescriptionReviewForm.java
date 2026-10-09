package com.healthcart.web.dto;

import com.healthcart.domain.PrescriptionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PrescriptionReviewForm {

    @NotNull
    private Long prescriptionId;

    @NotNull(message = "Review decision is required")
    private PrescriptionStatus status;

    @Size(max = 500, message = "Review notes cannot exceed 500 characters")
    private String reviewNotes;

    public PrescriptionReviewForm() {
    }

    public Long getPrescriptionId() {
        return prescriptionId;
    }

    public void setPrescriptionId(Long prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public void setStatus(PrescriptionStatus status) {
        this.status = status;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}
