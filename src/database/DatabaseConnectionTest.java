package database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * DatabaseConnectionTest
 *
 * Verifies Phase 2 requirements:
 * 1. Successful connection using DatabaseConnection utility
 * 2. Table availability (users, complaints, complaint_history)
 * 3. Handling of invalid credentials
 * 4. Handling of unavailable database
 */
public class DatabaseConnectionTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   HOSTEL COMPLAINT SYSTEM - DATABASE TESTS      ");
        System.out.println("=================================================");

        int passed = 0;
        int failed = 0;

        // Test 1: Successful connection
        System.out.print("\n[TEST 1] Testing Successful DB Connection... ");
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT DATABASE(), VERSION()");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("PASSED");
                        System.out.println("         Connected to DB: " + rs.getString(1) + ", MySQL Version: " + rs.getString(2));
                        passed++;
                    } else {
                        System.out.println("FAILED (No query results)");
                        failed++;
                    }
                }
            } else {
                System.out.println("FAILED (Connection is closed or invalid)");
                failed++;
            }
        } catch (SQLException e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 2: Table Availability
        System.out.print("\n[TEST 2] Testing Required Tables in hostel_complaint_db... ");
        try (Connection conn = DatabaseConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            Set<String> expectedTables = Set.of("users", "complaints", "complaint_history");
            Set<String> actualTables = new HashSet<>();

            try (ResultSet rs = meta.getTables("hostel_complaint_db", null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    actualTables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }

            boolean allPresent = true;
            for (String table : expectedTables) {
                if (!actualTables.contains(table)) {
                    allPresent = false;
                    System.out.println("\n         Missing table: " + table);
                }
            }

            if (allPresent) {
                System.out.println("PASSED");
                System.out.println("         Found tables: " + actualTables);
                passed++;
            } else {
                System.out.println("FAILED");
                failed++;
            }
        } catch (SQLException e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 3: Invalid Database Credentials
        System.out.print("\n[TEST 3] Testing Invalid Credentials Rejection... ");
        try {
            String url = DatabaseConnection.getDbUrl();
            String user = DatabaseConnection.getDbUsername();
            String wrongPass = "definitely_wrong_password_99999";

            DriverManager.getConnection(url, user, wrongPass);
            System.out.println("FAILED (Connection unexpectedly succeeded with wrong password)");
            failed++;
        } catch (SQLException e) {
            System.out.println("PASSED");
            System.out.println("         Correctly rejected invalid credentials with SQLState: " + e.getSQLState() + " (" + e.getMessage() + ")");
            passed++;
        }

        // Test 4: Database Unavailable / Non-Existent
        System.out.print("\n[TEST 4] Testing Unavailable Database Rejection... ");
        try {
            String badUrl = "jdbc:mysql://localhost:3306/non_existent_hostel_db_9999?useSSL=false";
            String user = DatabaseConnection.getDbUsername();
            // Obtain actual password through connection test
            DriverManager.getConnection(badUrl, user, "Prabal12345@%");
            System.out.println("FAILED (Unexpectedly connected to non-existent DB)");
            failed++;
        } catch (SQLException e) {
            System.out.println("PASSED");
            System.out.println("         Correctly rejected non-existent DB with SQLState: " + e.getSQLState() + " (" + e.getMessage() + ")");
            passed++;
        }

        System.out.println("\n=================================================");
        System.out.println("TEST SUMMARY: " + passed + " Passed, " + failed + " Failed");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
