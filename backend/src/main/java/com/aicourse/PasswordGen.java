package com.aicourse;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGen {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("123456");
        System.out.println("BCrypt hash for 123456: " + hash);
        System.out.println("Verify: " + encoder.matches("123456", hash));
        // Also verify the existing hash
        String existingHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        System.out.println("Existing hash matches 123456: " + encoder.matches("123456", existingHash));
    }
}
