package com.healthcart.domain;

public enum OrderStatus {
    PLACED("Payment Pending"),
    PAID("Paid"),
    CONFIRMED("Confirmed"),
    PACKED("Packed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    PAYMENT_FAILED("Payment Failed");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
