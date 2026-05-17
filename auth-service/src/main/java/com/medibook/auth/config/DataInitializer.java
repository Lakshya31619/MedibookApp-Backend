package com.medibook.auth.config;

import com.medibook.auth.entity.User;
import com.medibook.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            Thread.sleep(1000);

            log.info("🔧 Initializing admin user...");

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
                log.info("   🔐 Password: Admin@123456");
            } else {
                log.info("✅ Admin user already exists, skipping initialization");
            }

        } catch (InterruptedException e) {
            log.warn("⏸️ DataInitializer interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ Error during data initialization: {}", e.getMessage(), e);
        }
    }
}