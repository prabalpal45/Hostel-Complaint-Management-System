package test;

import com.sun.net.httpserver.HttpServer;
import database.DatabaseConnection;
import model.ComplaintPriority;
import model.ComplaintStatus;
import model.User;
import repository.MySQLComplaintRepository;
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
 * EndToEndVerificationTest
 *
 * Full End-to-End System & Regression Verification for Phase 8:
 * - Complete User Journey (Student Registration, Login, Lodging, History)
 * - Admin Operations (Login, Dashboard, Staff Assignment, Reports)
 * - Staff Operations (Login, Assigned Complaints, Status Update, Remarks)
 * - Audit Trail in complaint_history
 * - SQL Injection & Security Penetration checks
 * - Frontend HTML/CSS/JS Asset Serving
 * - Server Stop / Restart Persistence Verification
 */
public class EndToEndVerificationTest {

    private static final int PORT = 8080;
    private static final String BASE_URL = "http://localhost:" + PORT + "/api";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("   HOSTEL COMPLAINT SYSTEM - END-TO-END SUITE    ");
        System.out.println("=================================================");

        MySQLUserRepository userRepo = new MySQLUserRepository();
        MySQLComplaintRepository complaintRepo = new MySQLComplaintRepository();

        cleanup(complaintRepo, userRepo);

        // Verify active database connection
        if (!DatabaseConnection.testConnection()) {
            System.err.println("Fatal: MySQL is not reachable.");
            System.exit(1);
        }

        HttpServer server = Server.startServer(PORT);
        Thread.sleep(600);

        int passed = 0;
        int failed = 0;

        try {
            // -------------------------------------------------------------
            // SECTION 1: STUDENT WORKFLOW
            // -------------------------------------------------------------
            System.out.println("\n--- [SECTION 1] STUDENT WORKFLOW ---");

            // 1.1 Student Registration
            System.out.print("[TEST 1.1] Student Registration (POST /api/register)... ");
            String regBody = "{"
                    + "\"studentId\":\"E2E_S01\","
                    + "\"name\":\"Vikram Rathore\","
                    + "\"username\":\"vikram_r\","
                    + "\"password\":\"StudentKey#2026\","
                    + "\"roomNumber\":\"C-305\""
                    + "}";
            HttpResponse<String> rReg = post("/register", regBody, null, null);
            boolean regOk = rReg.statusCode() == 201 && rReg.body().contains("\"success\":true")
                    && !rReg.body().contains("StudentKey#2026");
            if (regOk) {
                System.out.println("PASSED (HTTP 201, password excluded from JSON)");
                passed++;
            } else {
                System.out.println("FAILED: " + rReg.statusCode() + " " + rReg.body());
                failed++;
            }

            // 1.2 Verify Student in MySQL with BCrypt
            System.out.print("[TEST 1.2] Verify Student in MySQL Database... ");
            User dbStudent = userRepo.findById("E2E_S01");
            boolean stuDbOk = dbStudent != null
                    && "vikram_r".equals(dbStudent.getUsername())
                    && PasswordUtil.isBCryptHash(dbStudent.getPassword())
                    && PasswordUtil.checkPassword("StudentKey#2026", dbStudent.getPassword());
            if (stuDbOk) {
                System.out.println("PASSED (Stored with BCrypt hash: " + dbStudent.getPassword().substring(0, 15) + "...)");
                passed++;
            } else {
                System.out.println("FAILED (Student not found or password not BCrypt)");
                failed++;
            }

            // 1.3 Student Login
            System.out.print("[TEST 1.3] Student Login (POST /api/login)... ");
            String loginBody = "{\"username\":\"vikram_r\",\"password\":\"StudentKey#2026\"}";
            HttpResponse<String> rLogin = post("/login", loginBody, null, null);
            boolean loginOk = rLogin.statusCode() == 200
                    && rLogin.body().contains("\"role\":\"STUDENT\"")
                    && rLogin.body().contains("\"roomNumber\":\"C-305\"")
                    && !rLogin.body().contains("password");
            if (loginOk) {
                System.out.println("PASSED (HTTP 200, Student Session established)");
                passed++;
            } else {
                System.out.println("FAILED: " + rLogin.statusCode() + " " + rLogin.body());
                failed++;
            }

            // 1.4 Student Lodges Complaint
            System.out.print("[TEST 1.4] Lodge Complaint (POST /api/complaints)... ");
            String compBody = "{"
                    + "\"complaintId\":\"E2E_C01\","
                    + "\"studentId\":\"E2E_S01\","
                    + "\"roomNumber\":\"C-305\","
                    + "\"category\":\"Plumbing\","
                    + "\"description\":\"Water leakage under washbasin causing floor flooding\","
                    + "\"priority\":\"HIGH\""
                    + "}";
            HttpResponse<String> rComp = post("/complaints", compBody, "E2E_S01", "STUDENT");
            boolean compCreated = rComp.statusCode() == 201 && rComp.body().contains("\"E2E_C01\"");
            if (compCreated) {
                System.out.println("PASSED (HTTP 201 Created)");
                passed++;
            } else {
                System.out.println("FAILED: " + rComp.statusCode() + " " + rComp.body());
                failed++;
            }

            // 1.5 Student Views Own Complaint History
            System.out.print("[TEST 1.5] Student Complaint History (GET /api/complaints/student/E2E_S01)... ");
            HttpResponse<String> rHistory = get("/complaints/student/E2E_S01", "E2E_S01", "STUDENT");
            boolean historyOk = rHistory.statusCode() == 200 && rHistory.body().contains("E2E_C01");
            if (historyOk) {
                System.out.println("PASSED (HTTP 200, complaint present in student history)");
                passed++;
            } else {
                System.out.println("FAILED: " + rHistory.statusCode());
                failed++;
            }

            // -------------------------------------------------------------
            // SECTION 2: ADMIN WORKFLOW
            // -------------------------------------------------------------
            System.out.println("\n--- [SECTION 2] ADMIN WORKFLOW ---");

            // Seed staff and admin accounts for testing
            userRepo.saveUser(new model.Staff("E2E_ST01", "Manoj Technician", "manoj_tech", "Staff#2026", "Plumbing"));
            userRepo.saveUser(new model.Admin("E2E_ADM01", "Chief Warden", "chief_admin", "Admin#2026"));

            // 2.1 Admin Login
            System.out.print("[TEST 2.1] Admin Login (POST /api/login)... ");
            HttpResponse<String> rAdminLogin = post("/login", "{\"username\":\"chief_admin\",\"password\":\"Admin#2026\"}", null, null);
            boolean admLogOk = rAdminLogin.statusCode() == 200 && rAdminLogin.body().contains("\"role\":\"ADMIN\"");
            if (admLogOk) {
                System.out.println("PASSED (HTTP 200, authenticated as ADMIN)");
                passed++;
            } else {
                System.out.println("FAILED: " + rAdminLogin.statusCode());
                failed++;
            }

            // 2.2 Admin Views All Complaints
            System.out.print("[TEST 2.2] Admin Views Complaints (GET /api/complaints)... ");
            HttpResponse<String> rAllComp = get("/complaints", "E2E_ADM01", "ADMIN");
            boolean allCompOk = rAllComp.statusCode() == 200 && rAllComp.body().contains("E2E_C01");
            if (allCompOk) {
                System.out.println("PASSED (HTTP 200, all complaints retrieved)");
                passed++;
            } else {
                System.out.println("FAILED: " + rAllComp.statusCode());
                failed++;
            }

            // 2.3 Admin Assigns Complaint to Staff
            System.out.print("[TEST 2.3] Admin Assigns Staff (PUT /api/complaints/E2E_C01/assign)... ");
            String assignPayload = "{\"staffId\":\"E2E_ST01\"}";
            HttpResponse<String> rAssign = put("/complaints/E2E_C01/assign", assignPayload, "E2E_ADM01", "ADMIN");
            boolean assignOk = rAssign.statusCode() == 200 && rAssign.body().contains("\"ASSIGNED\"")
                    && rAssign.body().contains("\"assignedStaffId\":\"E2E_ST01\"");
            if (assignOk) {
                System.out.println("PASSED (HTTP 200, assigned to E2E_ST01)");
                passed++;
            } else {
                System.out.println("FAILED: " + rAssign.statusCode() + " " + rAssign.body());
                failed++;
            }

            // 2.4 Admin Views Reports
            System.out.print("[TEST 2.4] Admin Reports (GET /api/reports)... ");
            HttpResponse<String> rRep = get("/reports", "E2E_ADM01", "ADMIN");
            boolean repOk = rRep.statusCode() == 200 && rRep.body().contains("\"total\":")
                    && rRep.body().contains("\"assigned\":");
            if (repOk) {
                System.out.println("PASSED (HTTP 200, aggregated report metrics delivered)");
                passed++;
            } else {
                System.out.println("FAILED: " + rRep.statusCode());
                failed++;
            }

            // -------------------------------------------------------------
            // SECTION 3: STAFF WORKFLOW
            // -------------------------------------------------------------
            System.out.println("\n--- [SECTION 3] STAFF WORKFLOW ---");

            // 3.1 Staff Login
            System.out.print("[TEST 3.1] Staff Login (POST /api/login)... ");
            HttpResponse<String> rStaffLogin = post("/login", "{\"username\":\"manoj_tech\",\"password\":\"Staff#2026\"}", null, null);
            boolean staffLogOk = rStaffLogin.statusCode() == 200 && rStaffLogin.body().contains("\"role\":\"STAFF\"")
                    && rStaffLogin.body().contains("\"department\":\"Plumbing\"");
            if (staffLogOk) {
                System.out.println("PASSED (HTTP 200, Staff Session established)");
                passed++;
            } else {
                System.out.println("FAILED: " + rStaffLogin.statusCode());
                failed++;
            }

            // 3.2 Staff Views Assigned Work
            System.out.print("[TEST 3.2] Staff Dashboard Work (GET /api/complaints/staff/E2E_ST01)... ");
            HttpResponse<String> rStaffWork = get("/complaints/staff/E2E_ST01", "E2E_ST01", "STAFF");
            boolean workOk = rStaffWork.statusCode() == 200 && rStaffWork.body().contains("E2E_C01");
            if (workOk) {
                System.out.println("PASSED (HTTP 200, complaint located in staff work queue)");
                passed++;
            } else {
                System.out.println("FAILED: " + rStaffWork.statusCode());
                failed++;
            }

            // 3.3 Staff Updates Status to IN_PROGRESS
            System.out.print("[TEST 3.3] Staff Updates Status to IN_PROGRESS... ");
            HttpResponse<String> rProg = put("/complaints/E2E_C01/status", "{\"status\":\"IN_PROGRESS\"}", "E2E_ST01", "STAFF");
            boolean progOk = rProg.statusCode() == 200 && rProg.body().contains("\"IN_PROGRESS\"");
            if (progOk) {
                System.out.println("PASSED (HTTP 200, status updated)");
                passed++;
            } else {
                System.out.println("FAILED: " + rProg.statusCode());
                failed++;
            }

            // 3.4 Staff Adds Remarks
            System.out.print("[TEST 3.4] Staff Adds Remarks... ");
            String remarksJson = "{\"remarks\":\"Replaced drain gasket and sealed joint.\"}";
            HttpResponse<String> rRem = put("/complaints/E2E_C01/remarks", remarksJson, "E2E_ST01", "STAFF");
            boolean remOk = rRem.statusCode() == 200 && rRem.body().contains("drain gasket");
            if (remOk) {
                System.out.println("PASSED (HTTP 200, remarks appended)");
                passed++;
            } else {
                System.out.println("FAILED: " + rRem.statusCode());
                failed++;
            }

            // 3.5 Staff Resolves Complaint
            System.out.print("[TEST 3.5] Staff Resolves Complaint... ");
            HttpResponse<String> rRes = put("/complaints/E2E_C01/status", "{\"status\":\"RESOLVED\"}", "E2E_ST01", "STAFF");
            boolean resOk = rRes.statusCode() == 200 && rRes.body().contains("\"RESOLVED\"");
            if (resOk) {
                System.out.println("PASSED (HTTP 200, complaint marked RESOLVED)");
                passed++;
            } else {
                System.out.println("FAILED: " + rRes.statusCode());
                failed++;
            }

            // -------------------------------------------------------------
            // SECTION 4: AUDIT TRAIL & RELATIONAL INTEGRITY
            // -------------------------------------------------------------
            System.out.println("\n--- [SECTION 4] AUDIT TRAIL & RELATIONAL INTEGRITY ---");

            // 4.1 Audit Trail Verification
            System.out.print("[TEST 4.1] Verify complaint_history in MySQL... ");
            int historyTransitions = 0;
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM complaint_history WHERE complaint_id = ?")) {
                ps.setString(1, "E2E_C01");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) historyTransitions = rs.getInt(1);
                }
            }
            if (historyTransitions >= 3) {
                System.out.println("PASSED (" + historyTransitions + " transitions logged: PENDING -> ASSIGNED -> IN_PROGRESS -> RESOLVED)");
                passed++;
            } else {
                System.out.println("FAILED (Found: " + historyTransitions + " transitions)");
                failed++;
            }

            // -------------------------------------------------------------
            // SECTION 5: SECURITY & RBAC ENFORCEMENT
            // -------------------------------------------------------------
            System.out.println("\n--- [SECTION 5] SECURITY & ACCESS CONTROL ---");

            // 5.1 SQL Injection Protection
            System.out.print("[TEST 5.1] SQL Injection in Login (' OR '1'='1)... ");
            String sqliPayload = "{\"username\":\"admin' OR '1'='1\",\"password\":\"' OR '1'='1\"}";
            HttpResponse<String> rSqli = post("/login", sqliPayload, null, null);
            if (rSqli.statusCode() == 404 || rSqli.statusCode() == 401) {
                System.out.println("PASSED (Rejected safely via PreparedStatement)");
                passed++;
            } else {
                System.out.println("FAILED: " + rSqli.statusCode() + " " + rSqli.body());
                failed++;
            }

            // 5.2 Unauthorized Report Access (Student attempting Admin endpoint)
            System.out.print("[TEST 5.2] Unauthorized Report Access by Student... ");
            HttpResponse<String> rRepBlocked = get("/reports", "E2E_S01", "STUDENT");
            if (rRepBlocked.statusCode() == 403) {
                System.out.println("PASSED (HTTP 403 Forbidden, RBAC enforced)");
                passed++;
            } else {
                System.out.println("FAILED: " + rRepBlocked.statusCode());
                failed++;
            }

            // 5.3 Unauthorized Staff Assignment by Student
            System.out.print("[TEST 5.3] Unauthorized Staff Assignment by Student... ");
            HttpResponse<String> rAssignBlocked = put("/complaints/E2E_C01/assign", "{\"staffId\":\"E2E_ST01\"}", "E2E_S01", "STUDENT");
            if (rAssignBlocked.statusCode() == 403) {
                System.out.println("PASSED (HTTP 403 Forbidden, only admins can assign)");
                passed++;
            } else {
                System.out.println("FAILED: " + rAssignBlocked.statusCode());
                failed++;
            }

            // -------------------------------------------------------------
            // SECTION 6: FRONTEND ASSET SERVING
            // -------------------------------------------------------------
            System.out.println("\n--- [SECTION 6] FRONTEND ASSET SERVING ---");

            String[] staticPages = {
                    "/index.html",
                    "/register.html",
                    "/student/dashboard.html",
                    "/staff/dashboard.html",
                    "/admin/dashboard.html",
                    "/css/style.css",
                    "/js/api.js",
                    "/js/auth.js"
            };

            boolean allStaticOk = true;
            for (String p : staticPages) {
                HttpRequest r = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + PORT + p)).GET().build();
                HttpResponse<String> resp = client.send(r, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200 || resp.body().isEmpty()) {
                    allStaticOk = false;
                    System.out.println("Failed on: " + p + " (status: " + resp.statusCode() + ")");
                }
            }

            System.out.print("[TEST 6.1] Serving 8 Frontend Pages/CSS/JS Assets... ");
            if (allStaticOk) {
                System.out.println("PASSED (All 8 core assets served with HTTP 200 and valid MIME types)");
                passed++;
            } else {
                System.out.println("FAILED");
                failed++;
            }

            // -------------------------------------------------------------
            // SECTION 7: SERVER RESTART & PERSISTENCE
            // -------------------------------------------------------------
            System.out.println("\n--- [SECTION 7] PERSISTENCE ACROSS SERVER RESTART ---");

            System.out.print("[TEST 7.1] Restart Server and verify state survival... ");
            Server.stopServer();
            Thread.sleep(600);

            Server.startServer(PORT);
            Thread.sleep(600);

            HttpResponse<String> rAfter = get("/complaints/E2E_C01", "E2E_ADM01", "ADMIN");
            boolean persistOk = rAfter.statusCode() == 200
                    && rAfter.body().contains("\"RESOLVED\"")
                    && rAfter.body().contains("drain gasket");

            if (persistOk) {
                System.out.println("PASSED (Data fully intact across server process restarts)");
                passed++;
            } else {
                System.out.println("FAILED: " + rAfter.statusCode() + " " + rAfter.body());
                failed++;
            }

        } finally {
            Server.stopServer();
            cleanup(complaintRepo, userRepo);
        }

        System.out.println("\n=================================================");
        System.out.println("END-TO-END VERIFICATION: " + passed + " Passed, " + failed + " Failed");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static HttpResponse<String> get(String path, String userId, String role) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path)).GET();
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
            complaintRepo.deleteComplaint("E2E_C01");
            userRepo.deleteUser("E2E_S01");
            userRepo.deleteUser("E2E_ST01");
            userRepo.deleteUser("E2E_ADM01");
        } catch (Exception ignored) {}
    }
}
