package com.alloca.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Scratch test — chạy để in BCrypt hash ra console.
 * Dùng output để seed Supabase dev DB.
 */
class BcryptHashGeneratorTest {

    @Test
    void printHashes() {
        var encoder = new BCryptPasswordEncoder(12);
        String[] passwords = {"Test@123456", "Admin@123456", "Manager@123"};
        System.out.println("\n========== BCrypt Hashes (rounds=12) ==========");
        for (String pwd : passwords) {
            System.out.printf("%-20s  →  %s%n", pwd, encoder.encode(pwd));
        }
        System.out.println("===============================================\n");
    }
}
