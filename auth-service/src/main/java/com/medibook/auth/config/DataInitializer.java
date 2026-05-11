package com.medibook.auth.config;

import com.medibook.auth.entity.User;
import com.medibook.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataInitializer — Creates the default admin user on startup if it doesn't
 * exist, and resets the AUTO_INCREMENT counter on the users table so that IDs
 * restart from (max_existing_id + 1) after manual row deletions.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            Thread.sleep(1000); // Wait for JPA initialization

            log.info("🔧 Initializing admin user...");

            // Check if admin user already exists
            var adminOpt = userRepository.findByEmail("admin@medibook.com");

            if (adminOpt.isEmpty()) {
                log.info("📝 Creating default admin user...");

                User adminUser = new User();
                adminUser.setFullName("MediBook Admin");
                adminUser.setEmail("admin@medibook.com");
                adminUser.setPasswordHash(passwordEncoder.encode("Admin@123456"));
                adminUser.setPhone("+1-800-MEDIBOOK");
                adminUser.setRole("ADMIN");
                adminUser.setActive(true);
                adminUser.setEmailVerified(true);
                adminUser.setProfilePicUrl(null);

                userRepository.save(adminUser);
                log.info("✅ Default admin user created successfully!");
                log.info("   📧 Email: admin@medibook.com");
                log.info("   🔐 Password: Admin@123456 (⚠️ Change this in production!)");
            } else {
                log.info("✅ Admin user already exists, skipping initialization");
            }

            // ---------------------------------------------------------------
            // AUTO_INCREMENT reset
            // MySQL keeps AUTO_INCREMENT at the historical high-water mark even
            // after rows are deleted.  Running ALTER TABLE … AUTO_INCREMENT = 1
            // tells MySQL to recalculate and set it to (MAX(userId) + 1), so
            // the next inserted user gets a compact ID instead of continuing
            // from the old maximum.
            // ---------------------------------------------------------------
            log.info("🔄 Resetting AUTO_INCREMENT on users table...");
            entityManager.createNativeQuery(
                "ALTER TABLE users AUTO_INCREMENT = 1"
            ).executeUpdate();
            log.info("✅ AUTO_INCREMENT reset complete.");

        } catch (InterruptedException e) {
            log.warn("⏸️ DataInitializer interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ Error during data initialization: {}", e.getMessage(), e);
            // Don't throw — let the application continue even if initialization fails
        }
    }
}