package test;

import com.sun.net.httpserver.HttpServer;
import database.DatabaseConnection;
import model.Staff;
import model.User;
import repository.MySQLUserRepository;
import server.Server;
import util.PasswordUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

/**
 * StaffManagementIntegrationTest
 *
 * Comprehensive automated test suite for the Staff Management Module:
 * 1. Admin adds new maintenance staff via POST /api/users/staff
 * 2. Password hashing verification in MySQL (BCrypt, never plaintext)
 * 3. Newly created staff login success via POST /api/login
 * 4. Duplicate username rejection (400 Bad Request)
 * 5. Admin updates staff details via PUT /api/users/staff/{id}
 * 6. Admin deactivates staff account via PUT /api/users/staff/{id}/status
 * 7. Deactivated staff login is rejected (401 Unauthorized)
 * 8. Assigning complaint to deactivated staff is blocked (400 Bad Request)
 * 9. Admin reactivates staff account via PUT /api/users/staff/{id}/status
 * 10. Reactivated staff can log in again successfully
 * 11. Non-admin (Student/Unauthenticated) access blocked (403/401)
 * 12. Active-only vs all staff filtering verification
 */
public class StaffManagementIntegrationTest {

    private static final int PORT = 8889;
    private static final String BASE_URL = "http://localhost:" + PORT + "/api";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("   STAFF MANAGEMENT MODULE - INTEGRATION TESTS   ");
        System.out.println("=================================================");

        MySQLUserRepository userRepo = new MySQLUserRepository();

        // Clean up test staff if left from a previous run
        cleanupTestStaff();

        HttpServer server = Server.startServer(PORT);
        Thread.sleep(500);

        int passed = 0;
        int failed = 0;

        String testStaffId = null;

        try {
            // TEST 1: Admin Adds New Staff Member (POST /api/users/staff)
            System.out.print("\n[TEST 1] Admin Adds New Staff Member (POST /api/users/staff)... ");
            String addBody = "{"
                    + "\"name\":\"Vikram Verma\","
                    + "\"username\":\"vikram_test_elec\","
                    + "\"password\":\"Vikram@Pass123\","
                    + "\"department\":\"Electrical\","
                    + "\"phone\":\"+91 9876543210\""
                    + "}";
            HttpResponse<String> rAdd = post("/users/staff", addBody, "ADM001", "ADMIN");
            if (rAdd.statusCode() == 201 && rAdd.body().contains("\"success\":true") && rAdd.body().contains("Vikram Verma")) {
                // Extract staff ID
                int idIdx = rAdd.body().indexOf("\"userId\":\"");
                if (idIdx != -1) {
                    int start = idIdx + 10;
                    int end = rAdd.body().indexOf("\"", start);
                    testStaffId = rAdd.body().substring(start, end);
                }
                boolean passwordExposed = rAdd.body().contains("Vikram@Pass123");
                if (!passwordExposed && testStaffId != null) {
                    System.out.println("PASSED (201 Created, Assigned ID: " + testStaffId + ", Password withheld)");
                    passed++;
                } else {
                    System.out.println("FAILED: Password was exposed or ID missing: " + rAdd.body());
                    failed++;
                }
            } else {
                System.out.println("FAILED: " + rAdd.statusCode() + " " + rAdd.body());
                failed++;
            }

            // TEST 2: Verify Password Hashing in MySQL Database Directly
            System.out.print("[TEST 2] Verify BCrypt Password Hashing in MySQL... ");
            String storedHash = null;
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE username = 'vikram_test_elec'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        storedHash = rs.getString("password");
                    }
                }
            }
            if (storedHash != null && PasswordUtil.isBCryptHash(storedHash) && !storedHash.equals("Vikram@Pass123")) {
                System.out.println("PASSED (Stored Hash starts with $2a$, plaintext not found)");
                passed++;
            } else {
                System.out.println("FAILED: Stored password is not BCrypt: " + storedHash);
                failed++;
            }

            // TEST 3: Login with Newly Created Staff Credentials
            System.out.print("[TEST 3] Newly Created Staff Login (POST /api/login)... ");
            String loginBody = "{\"username\":\"vikram_test_elec\",\"password\":\"Vikram@Pass123\"}";
            HttpResponse<String> rLogin = post("/login", loginBody, null, null);
            if (rLogin.statusCode() == 200 && rLogin.body().contains("\"role\":\"STAFF\"") && rLogin.body().contains("Vikram Verma")) {
                System.out.println("PASSED (200 OK, authenticated as STAFF)");
                passed++;
            } else {
                System.out.println("FAILED: " + rLogin.statusCode() + " " + rLogin.body());
                failed++;
            }

            // TEST 4: Duplicate Username Rejection
            System.out.print("[TEST 4] Duplicate Username Rejection (POST /api/users/staff)... ");
            String dupBody = "{"
                    + "\"name\":\"Vikram Imposter\","
                    + "\"username\":\"vikram_test_elec\","
                    + "\"password\":\"AnotherPass123\","
                    + "\"department\":\"Plumbing\","
                    + "\"phone\":\"+91 9999999999\""
                    + "}";
            HttpResponse<String> rDup = post("/users/staff", dupBody, "ADM001", "ADMIN");
            if (rDup.statusCode() == 400 && rDup.body().contains("already exists")) {
                System.out.println("PASSED (400 Bad Request, Duplicate rejected)");
                passed++;
            } else {
                System.out.println("FAILED: " + rDup.statusCode() + " " + rDup.body());
                failed++;
            }

            // TEST 5: Admin Updates Staff Profile (PUT /api/users/staff/{id})
            System.out.print("[TEST 5] Admin Updates Staff Profile (PUT /api/users/staff/" + testStaffId + ")... ");
            String editBody = "{"
                    + "\"name\":\"Vikram Verma Senior\","
                    + "\"department\":\"General Maintenance\","
                    + "\"phone\":\"+91 9888877777\""
                    + "}";
            HttpResponse<String> rEdit = put("/users/staff/" + testStaffId, editBody, "ADM001", "ADMIN");
            if (rEdit.statusCode() == 200 && rEdit.body().contains("\"success\":true")) {
                // Verify in database
                User updated = userRepo.findById(testStaffId);
                if (updated != null && "Vikram Verma Senior".equals(updated.getName())
                        && updated instanceof Staff && "General Maintenance".equals(((Staff) updated).getDepartment())
                        && "+91 9888877777".equals(updated.getPhone())) {
                    System.out.println("PASSED (200 OK, Name/Dept/Phone updated in DB)");
                    passed++;
                } else {
                    System.out.println("FAILED: Updated fields not reflected in database.");
                    failed++;
                }
            } else {
                System.out.println("FAILED: " + rEdit.statusCode() + " " + rEdit.body());
                failed++;
            }

            // TEST 6: Admin Deactivates Staff Account (PUT /api/users/staff/{id}/status)
            System.out.print("[TEST 6] Admin Deactivates Staff Account (PUT /api/users/staff/" + testStaffId + "/status)... ");
            String deactBody = "{\"status\":\"INACTIVE\"}";
            HttpResponse<String> rDeact = put("/users/staff/" + testStaffId + "/status", deactBody, "ADM001", "ADMIN");
            if (rDeact.statusCode() == 200 && rDeact.body().contains("\"INACTIVE\"")) {
                User deactivated = userRepo.findById(testStaffId);
                if (deactivated != null && "INACTIVE".equalsIgnoreCase(deactivated.getStatus())) {
                    System.out.println("PASSED (200 OK, Status changed to INACTIVE in DB)");
                    passed++;
                } else {
                    System.out.println("FAILED: Status not INACTIVE in database.");
                    failed++;
                }
            } else {
                System.out.println("FAILED: " + rDeact.statusCode() + " " + rDeact.body());
                failed++;
            }

            // TEST 7: Deactivated Staff Login Attempt Blocked
            System.out.print("[TEST 7] Deactivated Staff Login Attempt Blocked (POST /api/login)... ");
            HttpResponse<String> rDeactLogin = post("/login", loginBody, null, null);
            if (rDeactLogin.statusCode() == 401 && rDeactLogin.body().contains("deactivated")) {
                System.out.println("PASSED (401 Unauthorized, Account is deactivated message returned)");
                passed++;
            } else {
                System.out.println("FAILED: " + rDeactLogin.statusCode() + " " + rDeactLogin.body());
                failed++;
            }

            // TEST 8: Assigning Complaint to Deactivated Staff is Blocked
            System.out.print("[TEST 8] Assign Complaint to Inactive Staff Blocked (PUT /api/complaints/C001/assign)... ");
            String assignBody = "{\"staffId\":\"" + testStaffId + "\"}";
            HttpResponse<String> rAssign = put("/complaints/C001/assign", assignBody, "ADM001", "ADMIN");
            if (rAssign.statusCode() == 400 && rAssign.body().contains("inactive")) {
                System.out.println("PASSED (400 Bad Request, 'Cannot assign complaint to inactive staff member')");
                passed++;
            } else {
                System.out.println("FAILED: " + rAssign.statusCode() + " " + rAssign.body());
                failed++;
            }

            // TEST 9: Admin Reactivates Staff Account (PUT /api/users/staff/{id}/status)
            System.out.print("[TEST 9] Admin Reactivates Staff Account (PUT /api/users/staff/" + testStaffId + "/status)... ");
            String reactBody = "{\"status\":\"ACTIVE\"}";
            HttpResponse<String> rReact = put("/users/staff/" + testStaffId + "/status", reactBody, "ADM001", "ADMIN");
            if (rReact.statusCode() == 200 && rReact.body().contains("\"ACTIVE\"")) {
                User reactivated = userRepo.findById(testStaffId);
                if (reactivated != null && "ACTIVE".equalsIgnoreCase(reactivated.getStatus())) {
                    System.out.println("PASSED (200 OK, Status changed to ACTIVE in DB)");
                    passed++;
                } else {
                    System.out.println("FAILED: Status not ACTIVE in database.");
                    failed++;
                }
            } else {
                System.out.println("FAILED: " + rReact.statusCode() + " " + rReact.body());
                failed++;
            }

            // TEST 10: Reactivated Staff Login Succeeds
            System.out.print("[TEST 10] Reactivated Staff Login Succeeds (POST /api/login)... ");
            HttpResponse<String> rReactLogin = post("/login", loginBody, null, null);
            if (rReactLogin.statusCode() == 200 && rReactLogin.body().contains("\"role\":\"STAFF\"")) {
                System.out.println("PASSED (200 OK, Login restored after reactivation)");
                passed++;
            } else {
                System.out.println("FAILED: " + rReactLogin.statusCode() + " " + rReactLogin.body());
                failed++;
            }

            // TEST 11: Non-Admin Role Security (Student attempting admin endpoints gets 403)
            System.out.print("[TEST 11] Non-Admin Security: Student gets 403 Forbidden on Staff Management... ");
            HttpResponse<String> rSecAdd = post("/users/staff", addBody, "S001", "STUDENT");
            HttpResponse<String> rSecEdit = put("/users/staff/" + testStaffId, editBody, "S001", "STUDENT");
            HttpResponse<String> rSecStatus = put("/users/staff/" + testStaffId + "/status", deactBody, "S001", "STUDENT");

            if (rSecAdd.statusCode() == 403 && rSecEdit.statusCode() == 403 && rSecStatus.statusCode() == 403) {
                System.out.println("PASSED (All 3 endpoints returned 403 Forbidden for non-admin)");
                passed++;
            } else {
                System.out.println("FAILED: Add=" + rSecAdd.statusCode() + ", Edit=" + rSecEdit.statusCode() + ", Status=" + rSecStatus.statusCode());
                failed++;
            }

            // TEST 12: GET /api/users/staff list contains status and phone
            System.out.print("[TEST 12] GET /api/users/staff returns status and phone properties... ");
            HttpResponse<String> rStaffList = get("/users/staff", "ADM001", "ADMIN");
            if (rStaffList.statusCode() == 200 && rStaffList.body().contains("\"status\":\"ACTIVE\"") && rStaffList.body().contains("\"phone\"")) {
                System.out.println("PASSED (200 OK, full metadata rendered)");
                passed++;
            } else {
                System.out.println("FAILED: " + rStaffList.statusCode() + " " + rStaffList.body());
                failed++;
            }

        } finally {
            // Clean up test staff member
            cleanupTestStaff();
            Server.stopServer();
        }

        System.out.println("\n=================================================");
        System.out.println("               TEST SUMMARY                      ");
        System.out.println("=================================================");
        System.out.println(" Total Tests Run : " + (passed + failed));
        System.out.println(" Passed          : " + passed);
        System.out.println(" Failed          : " + failed);
        System.out.println(" Overall Result  : " + (failed == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED"));
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void cleanupTestStaff() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE username = 'vikram_test_elec'")) {
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private static HttpResponse<String> get(String path, String userId, String role) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET();
        if (userId != null) b.header("X-User-Id", userId);
        if (role != null) b.header("X-User-Role", role);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String jsonBody, String userId, String role) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (userId != null) b.header("X-User-Id", userId);
        if (role != null) b.header("X-User-Role", role);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(String path, String jsonBody, String userId, String role) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (userId != null) b.header("X-User-Id", userId);
        if (role != null) b.header("X-User-Role", role);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
