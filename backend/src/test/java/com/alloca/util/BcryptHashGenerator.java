package com.alloca.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Scratch utility — chạy main() để generate BCrypt hash cho seed data.
 * KHÔNG phải production code. Chỉ dùng khi cần tạo hash thủ công.
 *
 * Run: .\mvnw.cmd test-compile; java -cp ... com.alloca.util.BcryptHashGenerator
 * Hoặc chạy thẳng từ IDE.
 */
public class BcryptHashGenerator {
    public static void main(String[] args) {
        var encoder = new BCryptPasswordEncoder(12);
        String[] passwords = {"Test@123456", "Admin@123456", "Manager@123"};
        for (String pwd : passwords) {
            System.out.printf("%-20s  →  %s%n", pwd, encoder.encode(pwd));
        }
    }
}
