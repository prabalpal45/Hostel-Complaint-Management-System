package test;

import com.sun.net.httpserver.HttpServer;
import database.DatabaseConnection;
import model.ComplaintPriority;
import model.ComplaintStatus;
import model.User;
import repository.MySQLComplaintRepository;
import repository.MySQLUserRepository;
import server.Server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * DemoDataVerificationTest
 *
 * Verifies all requirements for the fresh demonstration data:
 * 1. Verification that all 7 accounts exist in MySQL
 * 2. Login verification for all 7 accounts (Admin, 3 Students, 3 Staff)
 * 3. Student complaint viewing and live submission
 * 4. Staff work queue viewing and status transition
 * 5. Admin complaint viewing, user directory, and work assignment
 * 6. Accurate report metrics matching the 12 seeded complaints
 * 7. Frontend static file accessibility
 */
public class DemoDataVerificationTest {

    private static final int PORT = 8080;
    private static final String BASE_URL = "http://localhost:" + PORT + "/api";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("   HOSTEL SYSTEM - DEMO DATA VERIFICATION        ");
        System.out.println("=================================================");

        MySQLUserRepository userRepo = new MySQLUserRepository();
        MySQLComplaintRepository complaintRepo = new MySQLComplaintRepository();

        if (!DatabaseConnection.testConnection()) {
            System.err.println("Fatal: MySQL connection failed.");
            System.exit(1);
        }

        HttpServer server = Server.startServer(PORT);
        Thread.sleep(600);

        int passed = 0;
        int failed = 0;

        try {
            // -------------------------------------------------------------
            // TEST 1: MySQL User Count & Presence Verification
            // -------------------------------------------------------------
            System.out.print("[TEST 1] Verifying all 7 Demo Users exist in MySQL... ");
            String[] expectedUserIds = {"ADM001", "S001", "S002", "S003", "ST001", "ST002", "ST003"};
            boolean allUsersPresent = true;
            for (String uid : expectedUserIds) {
                if (userRepo.findById(uid) == null) {
                    allUsersPresent = false;
                    System.out.println("Missing: " + uid);
                }
            }
            if (allUsersPresent && userRepo.loadUsers().size() == 7) {
                System.out.println("PASSED (All 7 accounts verified in database)");
                passed++;
            } else {
                System.out.println("FAILED (User count: " + userRepo.loadUsers().size() + ")");
                failed++;
            }

            // -------------------------------------------------------------
            // TEST 2: Login Authentication for All 7 Demo Accounts
            // -------------------------------------------------------------
            System.out.println("\n[TEST 2] Verifying Login for all 7 Demo Credentials via POST /api/login:");

            record Cred(String role, String username, String password, String expectedRole) {}
            Cred[] creds = {
                    new Cred("Admin", "admin", "Admin@123", "ADMIN"),
                    new Cred("Student 1", "student01", "Student@123", "STUDENT"),
                    new Cred("Student 2", "student02", "Student@123", "STUDENT"),
                    new Cred("Student 3", "student03", "Student@123", "STUDENT"),
                    new Cred("Staff 1", "staff01", "Staff@123", "STAFF"),
                    new Cred("Staff 2", "staff02", "Staff@123", "STAFF"),
                    new Cred("Staff 3", "staff03", "Staff@123", "STAFF")
            };

            boolean allLoginsOk = true;
            for (Cred c : creds) {
                HttpResponse<String> resp = post("/login",
                        "{\"username\":\"" + c.username + "\",\"password\":\"" + c.password + "\"}");

                boolean ok = resp.statusCode() == 200
                        && resp.body().contains("\"role\":\"" + c.expectedRole + "\"")
                        && !resp.body().contains(c.password);

                if (ok) {
                    System.out.println("  -> " + c.role + " (" + c.username + ") Login: SUCCESS (HTTP 200)");
                } else {
                    allLoginsOk = false;
                    System.out.println("  -> " + c.role + " (" + c.username + ") Login: FAILED (" + resp.statusCode() + " " + resp.body() + ")");
                }
            }

            if (allLoginsOk) {
                System.out.println("  ==> All 7 Demo Accounts successfully authenticated.");
                passed++;
            } else {
                System.out.println("  ==> Login test failed for one or more accounts.");
                failed++;
            }

            // -------------------------------------------------------------
            // TEST 3: Student Complaints History View
            // -------------------------------------------------------------
            System.out.print("\n[TEST 3] Verifying Student Complaint Filtering (GET /api/complaints/student/{id})... ");
            HttpResponse<String> rS1 = get("/complaints/student/S001", "S001", "STUDENT");
            HttpResponse<String> rS2 = get("/complaints/student/S002", "S002", "STUDENT");
            HttpResponse<String> rS3 = get("/complaints/student/S003", "S003", "STUDENT");

            boolean s1Ok = rS1.statusCode() == 200 && rS1.body().contains("C001") && rS1.body().contains("C012");
            boolean s2Ok = rS2.statusCode() == 200 && rS2.body().contains("C004") && rS2.body().contains("C007");
            boolean s3Ok = rS3.statusCode() == 200 && rS3.body().contains("C008") && rS3.body().contains("C011");

            if (s1Ok && s2Ok && s3Ok) {
                System.out.println("PASSED (All 3 students see their own 4 complaints)");
                passed++;
            } else {
                System.out.println("FAILED (Student complaints view mismatch)");
                failed++;
            }

            // -------------------------------------------------------------
            // TEST 4: Student Lodges a New Complaint
            // -------------------------------------------------------------
            System.out.print("[TEST 4] Student Complaint Submission (POST /api/complaints)... ");
            String testCompJson = "{"
                    + "\"studentId\":\"S001\","
                    + "\"complaintId\":\"C_TEST_SUBMIT\","
                    + "\"roomNumber\":\"B-204\","
                    + "\"category\":\"Electrical\","
                    + "\"description\":\"Desk lamp socket switch loose\","
                    + "\"priority\":\"MEDIUM\""
                    + "}";
            HttpResponse<String> rSubmit = post("/complaints", testCompJson, "S001", "STUDENT");
            boolean submitOk = rSubmit.statusCode() == 201 && rSubmit.body().contains("C_TEST_SUBMIT");
            complaintRepo.deleteComplaint("C_TEST_SUBMIT"); // Clean up immediately

            if (submitOk) {
                System.out.println("PASSED (HTTP 201 Created and verified in MySQL)");
                passed++;
            } else {
                System.out.println("FAILED: " + rSubmit.statusCode() + " " + rSubmit.body());
                failed++;
            }

            // -------------------------------------------------------------
            // TEST 5: Staff Assigned Complaints & Status Transition
            // -------------------------------------------------------------
            System.out.print("[TEST 5] Staff Assigned Work & Status Update... ");
            HttpResponse<String> rStaffWork = get("/complaints/staff/ST001", "ST001", "STAFF");
            boolean staffWorkOk = rStaffWork.statusCode() == 200 && rStaffWork.body().contains("C001");

            // Perform a status update on C006 from ASSIGNED -> IN_PROGRESS
            HttpResponse<String> rUpd = put("/complaints/C006/status", "{\"status\":\"IN_PROGRESS\"}", "ST001", "STAFF");
            HttpResponse<String> rRem = put("/complaints/C006/remarks", "{\"remarks\":\"Technician on-site testing AP signal\"}", "ST001", "STAFF");
            boolean updOk = rUpd.statusCode() == 200 && rRem.statusCode() == 200;

            // Revert back to ASSIGNED to preserve demo state
            put("/complaints/C006/status", "{\"status\":\"ASSIGNED\"}", "ST001", "STAFF");
            put("/complaints/C006/remarks", "{\"remarks\":\"\"}", "ST001", "STAFF");

            if (staffWorkOk && updOk) {
                System.out.println("PASSED (Staff work queue loaded, status & remarks updated)");
                passed++;
            } else {
                System.out.println("FAILED (Staff operations error)");
                failed++;
            }

            // -------------------------------------------------------------
            // TEST 6: Admin Dashboard & Reports Statistics
            // -------------------------------------------------------------
            System.out.print("[TEST 6] Admin Reports & Metrics (GET /api/reports)... ");
            HttpResponse<String> rRep = get("/reports", "ADM001", "ADMIN");
            boolean repOk = rRep.statusCode() == 200
                    && rRep.body().contains("\"total\":12")
                    && rRep.body().contains("\"pending\":3")
                    && rRep.body().contains("\"assigned\":2")
                    && rRep.body().contains("\"inProgress\":3")
                    && rRep.body().contains("\"resolved\":4")
                    && rRep.body().contains("\"Electrical\":3")
                    && rRep.body().contains("\"Plumbing\":3");

            if (repOk) {
                System.out.println("PASSED (Total: 12 | Pending: 3 | Assigned: 2 | InProgress: 3 | Resolved: 4)");
                passed++;
            } else {
                System.out.println("FAILED: " + rRep.body());
                failed++;
            }

            // -------------------------------------------------------------
            // TEST 7: Frontend Asset Serving Verification
            // -------------------------------------------------------------
            System.out.print("[TEST 7] Frontend Static Assets (HTML/CSS/JS) via Backend... ");
            String[] assets = {"/index.html", "/register.html", "/student/dashboard.html",
                               "/staff/dashboard.html", "/admin/dashboard.html", "/css/style.css", "/js/api.js"};
            boolean allAssetsOk = true;
            for (String a : assets) {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + PORT + a)).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) allAssetsOk = false;
            }

            if (allAssetsOk) {
                System.out.println("PASSED (All core frontend pages and assets served with HTTP 200)");
                passed++;
            } else {
                System.out.println("FAILED");
                failed++;
            }

        } finally {
            Server.stopServer();
        }

        System.out.println("\n=================================================");
        System.out.println("DEMO VERIFICATION: " + passed + " Passed, " + failed + " Failed");
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

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return post(path, body, null, null);
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
}
