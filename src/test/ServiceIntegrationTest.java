package test;

import exception.ComplaintNotFoundException;
import exception.InvalidCredentialsException;
import exception.InvalidInputException;
import exception.UserNotFoundException;
import model.Admin;
import model.Complaint;
import model.ComplaintPriority;
import model.ComplaintStatus;
import model.Staff;
import model.Student;
import model.User;
import repository.MySQLComplaintRepository;
import repository.MySQLUserRepository;
import service.AssignmentService;
import service.ComplaintService;
import service.ReportService;
import service.UserService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Map;

/**
 * ServiceIntegrationTest
 *
 * Comprehensive Phase 5 Service Layer Test Suite:
 * 1. Register student & retrieve from MySQL
 * 2. Successful and unsuccessful login
 * 3. Register staff and admin
 * 4. Create complaint
 * 5. Retrieve complaints by student
 * 6. Assign complaint to staff
 * 7. Update complaint status
 * 8. Add remarks
 * 9. Verify complaint history
 * 10. Report statistics generation
 * 11. Service re-instantiation / persistence verification
 */
public class ServiceIntegrationTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   HOSTEL COMPLAINT SYSTEM - SERVICE TESTS       ");
        System.out.println("=================================================");

        MySQLUserRepository mySQLUserRepo = new MySQLUserRepository();
        MySQLComplaintRepository mySQLComplaintRepo = new MySQLComplaintRepository();

        cleanup(mySQLComplaintRepo, mySQLUserRepo);

        UserService userService = new UserService();
        ComplaintService complaintService = new ComplaintService();
        AssignmentService assignmentService = new AssignmentService();
        ReportService reportService = new ReportService();

        int passed = 0;
        int failed = 0;

        // Test 1: Register Student & Retrieve from MySQL
        System.out.print("\n[TEST 1] Register Student & Retrieve... ");
        try {
            Student student = new Student("S_SVC_01", "Service Student", "svc_student_1", "SecretPass123", "B-303");
            userService.registerUser(student);

            User retrieved = userService.findUserById("S_SVC_01");
            if (retrieved instanceof Student && "B-303".equals(((Student) retrieved).getRoomNumber())) {
                System.out.println("PASSED (Student registered and loaded from MySQL)");
                passed++;
            } else {
                System.out.println("FAILED (Student attributes mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 2: Successful and Unsuccessful Login
        System.out.print("\n[TEST 2] Authentication (Login Success & Failure)... ");
        try {
            // Correct credentials
            User loggedIn = userService.login("svc_student_1", "SecretPass123");
            boolean successOk = loggedIn != null && "S_SVC_01".equals(loggedIn.getUserId());

            // Wrong password
            boolean wrongPassFailed = false;
            try {
                userService.login("svc_student_1", "WrongPassword999");
            } catch (InvalidCredentialsException ice) {
                wrongPassFailed = true;
            }

            // Non-existent user
            boolean missingUserFailed = false;
            try {
                userService.login("ghost_user_does_not_exist", "Pass");
            } catch (UserNotFoundException unfe) {
                missingUserFailed = true;
            }

            if (successOk && wrongPassFailed && missingUserFailed) {
                System.out.println("PASSED (Login verified for correct, wrong password, and missing user)");
                passed++;
            } else {
                System.out.println("FAILED (Login validation did not behave as expected)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 3: Register Staff and Admin
        System.out.print("\n[TEST 3] Register Staff & Admin... ");
        try {
            Staff staff = new Staff("ST_SVC_01", "Service Staff", "svc_staff_1", "StaffPass123", "Plumbing");
            Admin admin = new Admin("ADM_SVC_01", "Service Admin", "svc_admin_1", "AdminPass123");

            userService.registerUser(staff);
            userService.registerUser(admin);

            User rStaff = userService.findUserByUsername("svc_staff_1");
            User rAdmin = userService.findUserByUsername("svc_admin_1");

            if (rStaff instanceof Staff && "Plumbing".equals(((Staff) rStaff).getDepartment())
                    && rAdmin instanceof Admin && "ADMIN".equals(rAdmin.getRole())) {
                System.out.println("PASSED (Staff and Admin registered and verified)");
                passed++;
            } else {
                System.out.println("FAILED (Staff/Admin retrieval mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 4: Create Complaint
        System.out.print("\n[TEST 4] Create Complaint... ");
        try {
            Complaint complaint = new Complaint(
                    "C_SVC_001",
                    "S_SVC_01",
                    "B-303",
                    "Plumbing",
                    "Bathroom sink pipe leaking water constantly",
                    ComplaintPriority.HIGH
            );
            complaintService.createComplaint(complaint);

            Complaint created = complaintService.getComplaintById("C_SVC_001");
            if (created != null && ComplaintStatus.PENDING == created.getStatus()
                    && "Plumbing".equals(created.getCategory())) {
                System.out.println("PASSED (Complaint created and verified)");
                passed++;
            } else {
                System.out.println("FAILED (Complaint creation mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 5: Retrieve Complaints by Student
        System.out.print("\n[TEST 5] Retrieve Complaints by Student... ");
        try {
            ArrayList<Complaint> stuList = complaintService.getComplaintsByStudent("S_SVC_01");
            boolean found = stuList.stream().anyMatch(c -> "C_SVC_001".equals(c.getComplaintId()));
            if (found) {
                System.out.println("PASSED (Found complaint for student S_SVC_01)");
                passed++;
            } else {
                System.out.println("FAILED (Complaint not found for student)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 6: Assign Complaint to Staff
        System.out.print("\n[TEST 6] Assign Complaint to Staff via AssignmentService... ");
        try {
            boolean assigned = assignmentService.assignComplaint("C_SVC_001", "ST_SVC_01", "ADM_SVC_01");
            Complaint updated = complaintService.getComplaintById("C_SVC_001");

            ArrayList<Complaint> staffComplaints = assignmentService.getComplaintsByStaff("ST_SVC_01");
            boolean inStaffList = staffComplaints.stream().anyMatch(c -> "C_SVC_001".equals(c.getComplaintId()));

            if (assigned && updated != null && "ST_SVC_01".equals(updated.getAssignedStaffId())
                    && ComplaintStatus.ASSIGNED == updated.getStatus() && inStaffList) {
                System.out.println("PASSED (Complaint assigned to ST_SVC_01)");
                passed++;
            } else {
                System.out.println("FAILED (Assignment state mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 7: Update Complaint Status
        System.out.print("\n[TEST 7] Update Status to IN_PROGRESS... ");
        try {
            boolean statusUpdated = complaintService.updateStatus("C_SVC_001", ComplaintStatus.IN_PROGRESS, "ST_SVC_01");
            Complaint updated = complaintService.getComplaintById("C_SVC_001");

            if (statusUpdated && updated.getStatus() == ComplaintStatus.IN_PROGRESS) {
                System.out.println("PASSED (Status updated to IN_PROGRESS)");
                passed++;
            } else {
                System.out.println("FAILED (Status update mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 8: Add Remarks
        System.out.print("\n[TEST 8] Add Remarks... ");
        try {
            boolean remarksAdded = complaintService.addRemarks("C_SVC_001", "Pipe washer replaced, testing seal.");
            Complaint updated = complaintService.getComplaintById("C_SVC_001");

            if (remarksAdded && updated.getRemarks().contains("washer replaced")) {
                System.out.println("PASSED (Remarks added successfully)");
                passed++;
            } else {
                System.out.println("FAILED (Remarks update mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 9: Verify Complaint History
        System.out.print("\n[TEST 9] Verify Complaint History in MySQL... ");
        try (Connection conn = database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT old_status, new_status, updated_by FROM complaint_history WHERE complaint_id = ? ORDER BY history_id ASC")) {

            ps.setString(1, "C_SVC_001");
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                if (count >= 2) {
                    System.out.println("PASSED (" + count + " history transitions tracked)");
                    passed++;
                } else {
                    System.out.println("FAILED (Expected at least 2 transitions, found: " + count + ")");
                    failed++;
                }
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 10: Generate Report Statistics from MySQL
        System.out.print("\n[TEST 10] Generate Report Statistics... ");
        try {
            int total = reportService.getTotalComplaints();
            int inProg = reportService.getComplaintsByStatus(ComplaintStatus.IN_PROGRESS);
            int inProgStream = reportService.getComplaintsByStatusUsingStream(ComplaintStatus.IN_PROGRESS);
            Map<String, Integer> cats = reportService.getCategoryCounts();

            if (total >= 1 && inProg >= 1 && inProg == inProgStream && cats.containsKey("Plumbing")) {
                System.out.println("PASSED (Total: " + total + ", InProgress: " + inProg + ", Category Plumbing: " + cats.get("Plumbing") + ")");
                passed++;
            } else {
                System.out.println("FAILED (Report metrics mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Test 11: Application Restart Simulation & Persistence Check
        System.out.print("\n[TEST 11] Application Restart Simulation & Data Persistence... ");
        try {
            // Re-instantiate new service objects (simulating full server restart)
            UserService newUserService = new UserService();
            ComplaintService newComplaintService = new ComplaintService();

            User reloadedUser = newUserService.findUserById("S_SVC_01");
            Complaint reloadedComplaint = newComplaintService.getComplaintById("C_SVC_001");

            boolean userPersisted = reloadedUser != null && "S_SVC_01".equals(reloadedUser.getUserId());
            boolean complaintPersisted = reloadedComplaint != null
                    && "C_SVC_001".equals(reloadedComplaint.getComplaintId())
                    && ComplaintStatus.IN_PROGRESS == reloadedComplaint.getStatus()
                    && "ST_SVC_01".equals(reloadedComplaint.getAssignedStaffId())
                    && reloadedComplaint.getRemarks().contains("washer replaced");

            if (userPersisted && complaintPersisted) {
                System.out.println("PASSED (All state persisted across service lifecycle restarts)");
                passed++;
            } else {
                System.out.println("FAILED (Persistence across restarts failed)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            failed++;
        }

        // Cleanup
        cleanup(mySQLComplaintRepo, mySQLUserRepo);

        System.out.println("\n=================================================");
        System.out.println("SERVICE TEST SUMMARY: " + passed + " Passed, " + failed + " Failed");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void cleanup(MySQLComplaintRepository complaintRepo, MySQLUserRepository userRepo) {
        try {
            complaintRepo.deleteComplaint("C_SVC_001");
            userRepo.deleteUser("S_SVC_01");
            userRepo.deleteUser("ST_SVC_01");
            userRepo.deleteUser("ADM_SVC_01");
        } catch (Exception ignored) {}
    }
}
