package com.healthcart.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "addresses", indexes = {
    @Index(name = "idx_addresses_user", columnList = "user_id")
})
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String city;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String state;

    @NotBlank
    @Pattern(regexp = "^[0-9]{5,6}$", message = "Postal code must be 5 to 6 digits")
    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @NotBlank
    @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "Please provide a valid phone number")
    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    public Address() {
    }

    public Address(User user, String fullName, String line1, String line2, String city, String state, String postalCode, String phone) {
        this.user = user;
        this.fullName = fullName;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.phone = phone;
    }

    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (fullName != null) sb.append(fullName).append(", ");
        sb.append(line1);
        if (line2 != null && !line2.isBlank()) sb.append(", ").append(line2);
        sb.append(", ").append(city).append(", ").append(state).append(" - ").append(postalCode);
        if (phone != null && !phone.isBlank()) sb.append(" | Ph: ").append(phone);
        return sb.toString();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getLine2() {
        return line2;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
