package com.alloca.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility chạy trực tiếp bằng Java để seed dữ liệu dev/test vào Supabase PostgreSQL.
 * Đọc cấu hình kết nối từ backend/.env và chạy file backend/scripts/dev-seed.sql.
 */
public class DevSeedRunner {

    public static void main(String[] args) throws Exception {
        runSeed();
    }

    public static void runSeed() throws Exception {
        Path envPath = findFile("backend/.env", ".env", "env", "backend/env");
        if (envPath == null) {
            System.err.println("Cannot find backend/.env file");
            return;
        }

        Map<String, String> env = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(envPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                    int eq = line.indexOf('=');
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    env.put(key, val);
                }
            }
        }

        String dbUrl = env.get("SUPABASE_DB_URL");
        String user = env.get("SUPABASE_DB_USERNAME");
        String pass = env.get("SUPABASE_DB_PASSWORD");

        if (dbUrl == null || user == null || pass == null) {
            System.err.println("Missing SUPABASE_DB_* credentials in " + envPath);
            return;
        }

        Path sqlPath = findFile("backend/scripts/dev-seed.sql", "scripts/dev-seed.sql");
        if (sqlPath == null) {
            System.err.println("Cannot find dev-seed.sql");
            return;
        }

        String sql = Files.readString(sqlPath);

        System.out.println("Connecting to PostgreSQL...");
        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass);
             Statement stmt = conn.createStatement()) {

            System.out.println("Executing " + sqlPath + "...");
            stmt.execute(sql);
            System.out.println("Dev seed executed successfully!");

            System.out.println("\n--- Current Departments in DB ---");
            try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM departments ORDER BY name")) {
                while (rs.next()) {
                    System.out.printf("  %s | %s%n", rs.getString("id"), rs.getString("name"));
                }
            }

            System.out.println("\n--- Current Users in DB ---");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT u.username, u.role, u.email, d.name AS dept_name " +
                    "FROM users u LEFT JOIN departments d ON u.department_id = d.id " +
                    "ORDER BY u.role, u.username")) {
                while (rs.next()) {
                    System.out.printf("  %-12s | %-10s | %-30s | %s%n",
                            rs.getString("username"),
                            rs.getString("role"),
                            rs.getString("email"),
                            rs.getString("dept_name") != null ? rs.getString("dept_name") : "None");
                }
            }
        }
    }

    private static Path findFile(String... candidates) {
        for (String c : candidates) {
            Path p = Paths.get(c);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }
}

