package test;

import com.sun.net.httpserver.HttpServer;
import database.DatabaseConnection;
import model.Admin;
import model.ComplaintPriority;
import model.Staff;
import repository.MySQLComplaintRepository;
import repository.MySQLUserRepository;
import server.Server;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

/**
 * HttpApiIntegrationTest
 *
 * Comprehensive end-to-end HTTP API test suite for Phase 6.
 * Starts Server on an ephemeral port (8888), tests every endpoint and status code,
 * verifies server-side security and RBAC, tests restart persistence, and cleans up.
 */
public class HttpApiIntegrationTest {

    private static final int PORT = 8888;
    private static final String BASE_URL = "http://localhost:" + PORT + "/api";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("     HOSTEL COMPLAINT SYSTEM - API TESTS         ");
        System.out.println("=================================================");

        MySQLUserRepository userRepo = new MySQLUserRepository();
        MySQLComplaintRepository complaintRepo = new MySQLComplaintRepository();

        cleanup(complaintRepo, userRepo);

        // Pre-seed staff and admin in MySQL for API tests
        Staff staff = new Staff("ST_API_01", "API Electrician", "api_staff_1", "StaffSecret123", "Electrical");
        Admin admin = new Admin("ADM_API_01", "API Chief Admin", "api_admin_1", "AdminSecret123");
        userRepo.saveUser(staff);
        userRepo.saveUser(admin);

        HttpServer server = Server.startServer(PORT);
        Thread.sleep(500);

        int passed = 0;
        int failed = 0;

        try {
            // Test 1: GET /api/health
            System.out.print("\n[TEST 1] GET /api/health... ");
            HttpResponse<String> rHealth = get("/health", null, null);
            if (rHealth.statusCode() == 200 && rHealth.body().contains("\"UP\"") && rHealth.body().contains("\"CONNECTED\"")) {
                System.out.println("PASSED (200 OK, Database Connected)");
                passed++;
            } else {
                System.out.println("FAILED: " + rHealth.statusCode() + " " + rHealth.body());
                failed++;
            }

            // Test 2: CORS Preflight OPTIONS
            System.out.print("\n[TEST 2] OPTIONS /api/login (CORS Preflight)... ");
            HttpRequest optReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/login"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> rOpt = client.send(optReq, HttpResponse.BodyHandlers.ofString());
            String allowOrigin = rOpt.headers().firstValue("Access-Control-Allow-Origin").orElse("");
            if (rOpt.statusCode() == 204 && "*".equals(allowOrigin)) {
                System.out.println("PASSED (204 No Content, CORS headers present)");
                passed++;
            } else {
                System.out.println("FAILED: " + rOpt.statusCode());
                failed++;
            }

            // Test 3: POST /api/register (Student Registration)
            System.out.print("\n[TEST 3] POST /api/register (New Student)... ");
            String regBody = "{"
                    + "\"studentId\":\"S_API_01\","
                    + "\"name\":\"API Alice\","
                    + "\"username\":\"api_alice_1\","
                    + "\"password\":\"StudentPass123\","
                    + "\"roomNumber\":\"D-404\""
                    + "}";
            HttpResponse<String> rReg = post("/register", regBody, null, null);
            boolean passAbsent = !rReg.body().contains("StudentPass123") && !rReg.body().contains("password");
            if (rReg.statusCode() == 201 && rReg.body().contains("\"success\":true") && passAbsent) {
                System.out.println("PASSED (201 Created, password withheld from response)");
                passed++;
            } else {
                System.out.println("FAILED: " + rReg.statusCode() + " " + rReg.body());
                failed++;
            }

            // Test 4: POST /api/register (Duplicate Username Rejection)
            System.out.print("\n[TEST 4] POST /api/register (Duplicate Username)... ");
            String dupBody = "{"
                    + "\"studentId\":\"S_API_DUP\","
                    + "\"name\":\"Another Name\","
                    + "\"username\":\"api_alice_1\","
                    + "\"password\":\"AnyPass\","
                    + "\"roomNumber\":\"D-405\""
                    + "}";
            HttpResponse<String> rDup = post("/register", dupBody, null, null);
            if (rDup.statusCode() == 400 && rDup.body().contains("already exists")) {
                System.out.println("PASSED (400 Bad Request, duplicate rejected)");
                passed++;
            } else {
                System.out.println("FAILED: " + rDup.statusCode() + " " + rDup.body());
                failed++;
            }

            // Test 5: POST /api/login (Success)
            System.out.print("\n[TEST 5] POST /api/login (Valid Credentials)... ");
            String loginBody = "{\"username\":\"api_alice_1\",\"password\":\"StudentPass123\"}";
            HttpResponse<String> rLogin = post("/login", loginBody, null, null);
            boolean loginNoPass = !rLogin.body().contains("StudentPass123") && !rLogin.body().contains("password");
            if (rLogin.statusCode() == 200 && rLogin.body().contains("\"role\":\"STUDENT\"") && loginNoPass) {
                System.out.println("PASSED (200 OK, authenticated as STUDENT)");
                passed++;
            } else {
                System.out.println("FAILED: " + rLogin.statusCode() + " " + rLogin.body());
                failed++;
            }

            // Test 6: POST /api/login (Invalid Password)
            System.out.print("\n[TEST 6] POST /api/login (Invalid Password)... ");
            String badPassBody = "{\"username\":\"api_alice_1\",\"password\":\"WrongPassword\"}";
            HttpResponse<String> rBadPass = post("/login", badPassBody, null, null);
            if (rBadPass.statusCode() == 401 && rBadPass.body().contains("Incorrect password")) {
                System.out.println("PASSED (401 Unauthorized)");
                passed++;
            } else {
                System.out.println("FAILED: " + rBadPass.statusCode() + " " + rBadPass.body());
                failed++;
            }

            // Test 7: POST /api/login (Non-existent User)
            System.out.print("\n[TEST 7] POST /api/login (Non-existent Username)... ");
            String noUserBody = "{\"username\":\"ghost_user_999\",\"password\":\"AnyPass\"}";
            HttpResponse<String> rNoUser = post("/login", noUserBody, null, null);
            if (rNoUser.statusCode() == 404) {
                System.out.println("PASSED (404 Not Found)");
                passed++;
            } else {
                System.out.println("FAILED: " + rNoUser.statusCode() + " " + rNoUser.body());
                failed++;
            }

            // Test 8: POST /api/complaints (Unauthenticated attempt)
            System.out.print("\n[TEST 8] POST /api/complaints (Without Authentication)... ");
            String compBody = "{"
                    + "\"category\":\"Electrical\","
                    + "\"roomNumber\":\"D-404\","
                    + "\"description\":\"Short circuit in power socket\","
                    + "\"priority\":\"HIGH\""
                    + "}";
            HttpResponse<String> rUnauth = post("/complaints", compBody, null, null);
            if (rUnauth.statusCode() == 401) {
                System.out.println("PASSED (401 Unauthorized)");
                passed++;
            } else {
                System.out.println("FAILED: " + rUnauth.statusCode() + " " + rUnauth.body());
                failed++;
            }

            // Test 9: POST /api/complaints (Student Identity Spoofing attempt)
            System.out.print("\n[TEST 9] POST /api/complaints (Student Impersonation Attempt)... ");
            String spoofBody = "{"
                    + "\"studentId\":\"S_OTHER_STUDENT\","
                    + "\"category\":\"Electrical\","
                    + "\"roomNumber\":\"D-404\","
                    + "\"description\":\"Short circuit in power socket\","
                    + "\"priority\":\"HIGH\""
                    + "}";
            HttpResponse<String> rSpoof = post("/complaints", spoofBody, "S_API_01", "STUDENT");
            if (rSpoof.statusCode() == 403) {
                System.out.println("PASSED (403 Forbidden, server rejected impersonation)");
                passed++;
            } else {
                System.out.println("FAILED: " + rSpoof.statusCode() + " " + rSpoof.body());
                failed++;
            }

            // Test 10: POST /api/complaints (Valid Submission as Student)
            System.out.print("\n[TEST 10] POST /api/complaints (Valid Submission)... ");
            String validCompBody = "{"
                    + "\"studentId\":\"S_API_01\","
                    + "\"complaintId\":\"C_API_001\","
                    + "\"category\":\"Electrical\","
                    + "\"roomNumber\":\"D-404\","
                    + "\"description\":\"Short circuit in power socket\","
                    + "\"priority\":\"HIGH\""
                    + "}";
            HttpResponse<String> rCompOk = post("/complaints", validCompBody, "S_API_01", "STUDENT");
            if (rCompOk.statusCode() == 201 && rCompOk.body().contains("\"C_API_001\"")) {
                System.out.println("PASSED (201 Created)");
                passed++;
            } else {
                System.out.println("FAILED: " + rCompOk.statusCode() + " " + rCompOk.body());
                failed++;
            }

            // Test 11: GET /api/complaints/student/S_API_01
            System.out.print("\n[TEST 11] GET /api/complaints/student/S_API_01... ");
            HttpResponse<String> rStuComp = get("/complaints/student/S_API_01", "S_API_01", "STUDENT");
            if (rStuComp.statusCode() == 200 && rStuComp.body().contains("\"C_API_001\"")) {
                System.out.println("PASSED (200 OK, complaint listed)");
                passed++;
            } else {
                System.out.println("FAILED: " + rStuComp.statusCode() + " " + rStuComp.body());
                failed++;
            }

            // Test 12: PUT /api/complaints/{id}/assign (Admin assigns staff)
            System.out.print("\n[TEST 12] PUT /api/complaints/C_API_001/assign (by Admin)... ");
            String assignBody = "{\"staffId\":\"ST_API_01\"}";
            HttpResponse<String> rAssign = put("/complaints/C_API_001/assign", assignBody, "ADM_API_01", "ADMIN");
            if (rAssign.statusCode() == 200 && rAssign.body().contains("\"ASSIGNED\"")) {
                System.out.println("PASSED (200 OK, assigned to ST_API_01)");
                passed++;
            } else {
                System.out.println("FAILED: " + rAssign.statusCode() + " " + rAssign.body());
                failed++;
            }

            // Test 13: PUT /api/complaints/{id}/status (Staff updates status)
            System.out.print("\n[TEST 13] PUT /api/complaints/C_API_001/status (by Assigned Staff)... ");
            String statusBody = "{\"status\":\"IN_PROGRESS\"}";
            HttpResponse<String> rStatus = put("/complaints/C_API_001/status", statusBody, "ST_API_01", "STAFF");
            if (rStatus.statusCode() == 200 && rStatus.body().contains("\"IN_PROGRESS\"")) {
                System.out.println("PASSED (200 OK, status updated)");
                passed++;
            } else {
                System.out.println("FAILED: " + rStatus.statusCode() + " " + rStatus.body());
                failed++;
            }

            // Test 14: PUT /api/complaints/{id}/remarks (Staff adds remarks)
            System.out.print("\n[TEST 14] PUT /api/complaints/C_API_001/remarks (by Assigned Staff)... ");
            String remarksBody = "{\"remarks\":\"Main line breaker isolated. Wiring restored.\"}";
            HttpResponse<String> rRemarks = put("/complaints/C_API_001/remarks", remarksBody, "ST_API_01", "STAFF");
            if (rRemarks.statusCode() == 200 && rRemarks.body().contains("Wiring restored")) {
                System.out.println("PASSED (200 OK, remarks persisted)");
                passed++;
            } else {
                System.out.println("FAILED: " + rRemarks.statusCode() + " " + rRemarks.body());
                failed++;
            }

            // Test 15: GET /api/reports (Admin only)
            System.out.print("\n[TEST 15] GET /api/reports (by Admin)... ");
            HttpResponse<String> rReport = get("/reports", "ADM_API_01", "ADMIN");
            if (rReport.statusCode() == 200 && rReport.body().contains("\"total\":") && rReport.body().contains("\"inProgress\":")) {
                System.out.println("PASSED (200 OK, reporting statistics delivered from MySQL)");
                passed++;
            } else {
                System.out.println("FAILED: " + rReport.statusCode() + " " + rReport.body());
                failed++;
            }

            // Test 16: GET /api/reports (Unauthorized Student attempt)
            System.out.print("\n[TEST 16] GET /api/reports (Unauthorized Student Attempt)... ");
            HttpResponse<String> rRepForbidden = get("/reports", "S_API_01", "STUDENT");
            if (rRepForbidden.statusCode() == 403) {
                System.out.println("PASSED (403 Forbidden, RBAC enforced)");
                passed++;
            } else {
                System.out.println("FAILED: " + rRepForbidden.statusCode() + " " + rRepForbidden.body());
                failed++;
            }

            // Test 17: Static Frontend File Serving
            System.out.print("\n[TEST 17] GET /index.html (Frontend Static File Serving)... ");
            HttpRequest staticReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + PORT + "/index.html"))
                    .GET()
                    .build();
            HttpResponse<String> rStatic = client.send(staticReq, HttpResponse.BodyHandlers.ofString());
            String ct = rStatic.headers().firstValue("Content-Type").orElse("");
            if (rStatic.statusCode() == 200 && ct.contains("text/html") && rStatic.body().contains("Hostel Complaint")) {
                System.out.println("PASSED (200 OK, frontend rendered)");
                passed++;
            } else {
                System.out.println("FAILED: " + rStatic.statusCode() + " " + ct);
                failed++;
            }

            // Test 18: Server Restart and Data Persistence
            System.out.print("\n[TEST 18] Server Restart & Persistence Check... ");
            Server.stopServer();
            Thread.sleep(500);

            // Re-start server
            Server.startServer(PORT);
            Thread.sleep(500);

            HttpResponse<String> rPersist = get("/complaints/C_API_001", "ADM_API_01", "ADMIN");
            if (rPersist.statusCode() == 200
                    && rPersist.body().contains("\"IN_PROGRESS\"")
                    && rPersist.body().contains("Wiring restored")) {
                System.out.println("PASSED (Data fully persisted across server process cycles)");
                passed++;
            } else {
                System.out.println("FAILED: " + rPersist.statusCode() + " " + rPersist.body());
                failed++;
            }

        } finally {
            Server.stopServer();
            cleanup(complaintRepo, userRepo);
        }

        System.out.println("\n=================================================");
        System.out.println("API TEST SUMMARY: " + passed + " Passed, " + failed + " Failed");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static HttpResponse<String> get(String path, String userId, String role) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET();
        if (userId != null) b.header("X-User-Id", userId);
        if (role != null) b.header("X-User-Role", role);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body, String userId, String role) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (userId != null) b.header("X-User-Id", userId);
        if (role != null) b.header("X-User-Role", role);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(String path, String body, String userId, String role) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (userId != null) b.header("X-User-Id", userId);
        if (role != null) b.header("X-User-Role", role);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void cleanup(MySQLComplaintRepository complaintRepo, MySQLUserRepository userRepo) {
        try {
            complaintRepo.deleteComplaint("C_API_001");
            userRepo.deleteUser("S_API_01");
            userRepo.deleteUser("S_API_DUP");
            userRepo.deleteUser("ST_API_01");
            userRepo.deleteUser("ADM_API_01");
        } catch (Exception ignored) {}
    }
}
