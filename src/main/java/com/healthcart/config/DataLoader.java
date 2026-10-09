package com.healthcart.config;

import com.healthcart.domain.*;
import com.healthcart.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("!prod")
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(
            UserRepository userRepository,
            SellerProfileRepository sellerProfileRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            AddressRepository addressRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("Database already initialized. Skipping seed loader.");
            return;
        }

        log.info("Populating development reference catalog and demo accounts...");

        // 1. Categories
        Category catVitamins = categoryRepository.save(new Category("Vitamins & Supplements", "vitamins-supplements", "Daily multivitamins, omega-3, minerals and dietary enhancers.", "capsule"));
        Category catRx = categoryRepository.save(new Category("Prescription Medicines", "prescription-medicines", "Doctor prescribed medications and acute therapies.", "heart-pulse"));
        Category catPersonalCare = categoryRepository.save(new Category("Personal Care", "personal-care", "Skin, dental, hygiene and body wellness products.", "sparkles"));
        Category catFirstAid = categoryRepository.save(new Category("First Aid & Devices", "first-aid-devices", "Bandages, BP monitors, thermometers and emergency supplies.", "bandaid"));
        Category catAyurveda = categoryRepository.save(new Category("Ayurvedic & Herbal", "ayurvedic-herbal", "Traditional herbal immunity boosters and natural remedies.", "flower1"));

        // 2. Demo Users
        User admin = new User("System Administrator", "admin@healthcart.com", passwordEncoder.encode("Admin@12345"), Role.ROLE_ADMIN);
        userRepository.save(admin);

        User seller = new User("Apothecary Pharma Ltd", "seller@healthcart.com", passwordEncoder.encode("Seller@12345"), Role.ROLE_SELLER);
        User savedSeller = userRepository.save(seller);
        sellerProfileRepository.save(new SellerProfile(savedSeller, "Apothecary Healthcare Logistics", "DL-MH-2024-88392"));

        User buyer = new User("Rahul Sharma", "buyer@healthcart.com", passwordEncoder.encode("Buyer@12345"), Role.ROLE_BUYER);
        User savedBuyer = userRepository.save(buyer);

        // Buyer default address
        addressRepository.save(new Address(
                savedBuyer,
                "Rahul Sharma",
                "Flat 402, Green Glen Heights, Bellandur",
                "Near Outer Ring Road",
                "Bengaluru",
                "Karnataka",
                "560103",
                "+91 98765 43210"
        ));

        // 3. Products
        List<Product> products = List.of(
                new Product(savedSeller, catVitamins, "Omega-3 Triple Strength Fish Oil (1000mg)",
                        "Ultra-pure molecularly distilled fish oil softgels containing high EPA and DHA to support cardiovascular health and joint mobility.",
                        new BigDecimal("799.00"), 45, 10, false),

                new Product(savedSeller, catVitamins, "Vitamin C 1000mg + Zinc Effervescent",
                        "Sugar-free fizzy immunity booster tablets formulated with natural Amla extract and elemental Zinc for daily protection.",
                        new BigDecimal("349.00"), 60, 15, false),

                new Product(savedSeller, catRx, "Amoxicillin & Potassium Clavulanate 625mg",
                        "Broad-spectrum antibiotic tablets for bacterial infections. Valid doctor prescription strictly required before dispensing.",
                        new BigDecimal("210.50"), 25, 5, true),

                new Product(savedSeller, catRx, "Metformin Hydrochloride SR 500mg",
                        "Sustained-release glycaemic control formulation for type 2 diabetes management. Requires doctor prescription verification.",
                        new BigDecimal("125.00"), 50, 10, true),

                new Product(savedSeller, catFirstAid, "Accu-Chek Blood Glucose Monitor Kit",
                        "Hospital-grade instant blood sugar monitoring kit with 25 test strips, lancing device and 10 sterile lancets included.",
                        new BigDecimal("1299.00"), 18, 5, false),

                new Product(savedSeller, catFirstAid, "Digital Infrared Forehead Thermometer",
                        "Non-contact clinical grade thermometer with 1-second rapid scanning and color-coded fever alarm display.",
                        new BigDecimal("849.00"), 3, 5, false), // Low stock trigger test item!

                new Product(savedSeller, catPersonalCare, "Ceramide & Hyaluronic Hydrating Cleanser",
                        "Dermatologically tested gentle foaming wash designed to restore skin barrier without stripping natural moisture.",
                        new BigDecimal("499.00"), 35, 8, false),

                new Product(savedSeller, catAyurveda, "Organic Ashwagandha KSM-66 Extract (60 Veg Caps)",
                        "Clinically proven root extract promoting stress relief, cognitive clarity, vitality and restorative sleep.",
                        new BigDecimal("599.00"), 40, 10, false)
        );

        productRepository.saveAll(products);

        log.info("Development data initialization completed successfully.");
    }
}
