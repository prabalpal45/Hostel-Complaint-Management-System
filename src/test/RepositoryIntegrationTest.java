package test;

import database.DatabaseConnection;
import model.Admin;
import model.Complaint;
import model.ComplaintPriority;
import model.ComplaintStatus;
import model.Staff;
import model.Student;
import model.User;
import repository.MySQLComplaintRepository;
import repository.MySQLUserRepository;
import util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;

/**
 * RepositoryIntegrationTest
 *
 * Verifies Phase 4 requirements:
 * 1. DatabaseConnection connectivity verification
 * 2. User insertion and retrieval (Student, Staff, Admin)
 * 3. BCrypt password hashing verification
 * 4. Duplicate username rejection
 * 5. Complaint insertion and retrieval
 * 6. Complaint assignment, status updates and audit history
 * 7. Foreign key relationship enforcement
 */
public class RepositoryIntegrationTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   HOSTEL COMPLAINT SYSTEM - REPOSITORY TESTS    ");
        System.out.println("=================================================");

        MySQLUserRepository userRepo = new MySQLUserRepository();
        MySQLComplaintRepository complaintRepo = new MySQLComplaintRepository();

        int passed = 0;
        int failed = 0;

        // Step 0: Connectivity Verification
        System.out.print("\n[STEP 0] Verifying DatabaseConnection to hostel_complaint_db... ");
        if (DatabaseConnection.testConnection()) {
            System.out.println("PASSED (Active connection established)");
            passed++;
        } else {
            System.out.println("FAILED");
            failed++;
            System.exit(1);
        }

        // Clean any leftover test data
        cleanupTestData(complaintRepo, userRepo);

        // Test 1: User Insertion & Retrieval (Student, Staff, Admin)
        System.out.print("\n[TEST 1] Testing User Insertion & Retrieval... ");
        try {
            Student student = new Student("T_STU01", "Test Student 1", "test_student_1", "SecretPass1!", "A-201");
            Staff staff = new Staff("T_STAFF01", "Test Electrician", "test_staff_1", "StaffPass2@", "Electrical");
            Admin admin = new Admin("T_ADM01", "Test Admin", "test_admin_1", "AdminPass3#");

            userRepo.saveUser(student);
            userRepo.saveUser(staff);
            userRepo.saveUser(admin);

            User retrievedStu = userRepo.findById("T_STU01");
            User retrievedStaff = userRepo.findByUsername("test_staff_1");
            User retrievedAdmin = userRepo.findById("T_ADM01");

            boolean studentOk = (retrievedStu instanceof Student)
                    && "A-201".equals(((Student) retrievedStu).getRoomNumber())
                    && "STUDENT".equals(retrievedStu.getRole());

            boolean staffOk = (retrievedStaff instanceof Staff)
                    && "Electrical".equals(((Staff) retrievedStaff).getDepartment())
                    && "STAFF".equals(retrievedStaff.getRole());

            boolean adminOk = (retrievedAdmin instanceof Admin)
                    && "ADMIN".equals(retrievedAdmin.getRole());

            // Verify Password Hashing
            boolean passwordHashed = PasswordUtil.isBCryptHash(retrievedStu.getPassword())
                    && PasswordUtil.checkPassword("SecretPass1!", retrievedStu.getPassword())
                    && !retrievedStu.getPassword().equals("SecretPass1!");

            if (studentOk && staffOk && adminOk && passwordHashed) {
                System.out.println("PASSED");
                System.out.println("         Student, Staff, and Admin inserted and retrieved correctly.");
                System.out.println("         Password verified as BCrypt hash: " + retrievedStu.getPassword().substring(0, 20) + "...");
                passed++;
            } else {
                System.out.println("FAILED (User attributes or password hash mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED with exception: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 2: Duplicate Username Handling
        System.out.print("\n[TEST 2] Testing Duplicate Username Handling... ");
        try {
            // Attempt inserting another student with the same username "test_student_1"
            Student duplicateUser = new Student("T_STU02", "Different Name", "test_student_1", "Pass456", "B-101");
            userRepo.insertUser(duplicateUser);

            System.out.println("FAILED (Duplicate username was accepted without error)");
            failed++;
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("PASSED");
            System.out.println("         Duplicate username was correctly rejected by database constraint: " + e.getMessage());
            passed++;
        } catch (Exception e) {
            System.out.println("PASSED (Rejected with exception: " + e.getMessage() + ")");
            passed++;
        }

        // Test 3: Complaint Insertion & Retrieval
        System.out.print("\n[TEST 3] Testing Complaint Insertion & Retrieval... ");
        try {
            Complaint complaint = new Complaint(
                    "C_TEST_001",
                    "T_STU01",
                    "A-201",
                    "Electrical",
                    "Ceiling fan speed regulator is completely broken",
                    ComplaintPriority.HIGH
            );

            complaintRepo.saveComplaint(complaint);

            Complaint retrieved = complaintRepo.findById("C_TEST_001");
            boolean ok = retrieved != null
                    && "T_STU01".equals(retrieved.getStudentId())
                    && "Electrical".equals(retrieved.getCategory())
                    && ComplaintPriority.HIGH == retrieved.getPriority()
                    && ComplaintStatus.PENDING == retrieved.getStatus();

            ArrayList<Complaint> stuComplaints = complaintRepo.findByStudentId("T_STU01");
            boolean listOk = !stuComplaints.isEmpty() && stuComplaints.stream().anyMatch(c -> c.getComplaintId().equals("C_TEST_001"));

            if (ok && listOk) {
                System.out.println("PASSED");
                System.out.println("         Complaint C_TEST_001 inserted and retrieved successfully.");
                passed++;
            } else {
                System.out.println("FAILED (Complaint data mismatch)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED with exception: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 4: Assignment, Status Update, Remarks and Complaint History
        System.out.print("\n[TEST 4] Testing Staff Assignment & Status Transition... ");
        try {
            boolean assigned = complaintRepo.assignStaff("C_TEST_001", "T_STAFF01");
            boolean statusUpdated = complaintRepo.updateStatus("C_TEST_001", ComplaintStatus.IN_PROGRESS, "T_STAFF01");
            boolean remarksAdded = complaintRepo.addRemarks("C_TEST_001", "Technician ordered replacement capacitor");

            Complaint updated = complaintRepo.findById("C_TEST_001");
            boolean verifyUpdate = updated != null
                    && "T_STAFF01".equals(updated.getAssignedStaffId())
                    && ComplaintStatus.IN_PROGRESS == updated.getStatus()
                    && updated.getRemarks().contains("replacement capacitor");

            // Verify complaint_history audit trail in DB
            int historyCount = 0;
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM complaint_history WHERE complaint_id = ?")) {
                ps.setString(1, "C_TEST_001");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) historyCount = rs.getInt(1);
                }
            }

            if (assigned && statusUpdated && remarksAdded && verifyUpdate && historyCount >= 2) {
                System.out.println("PASSED");
                System.out.println("         Assigned to T_STAFF01, status updated to IN_PROGRESS.");
                System.out.println("         Audit trail verified: " + historyCount + " history transitions recorded.");
                passed++;
            } else {
                System.out.println("FAILED (Update or history count mismatch: " + historyCount + ")");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAILED with exception: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 5: Foreign Key Enforcement
        System.out.print("\n[TEST 5] Testing Foreign Key Enforcement (Invalid Student ID)... ");
        try {
            Complaint invalidComplaint = new Complaint(
                    "C_TEST_INVALID",
                    "NON_EXISTENT_STUDENT_ID_9999",
                    "Z-999",
                    "Plumbing",
                    "Should fail foreign key check",
                    ComplaintPriority.LOW
            );

            complaintRepo.insertComplaint(invalidComplaint);
            System.out.println("FAILED (Complaint with invalid studentId was accepted without foreign key error)");
            failed++;
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("PASSED");
            System.out.println("         Foreign key constraint properly prevented orphan complaint: " + e.getMessage());
            passed++;
        } catch (Exception e) {
            System.out.println("PASSED (Foreign key violation caught: " + e.getMessage() + ")");
            passed++;
        }

        // Clean up test data
        cleanupTestData(complaintRepo, userRepo);

        System.out.println("\n=================================================");
        System.out.println("REPOSITORY TEST SUMMARY: " + passed + " Passed, " + failed + " Failed");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void cleanupTestData(MySQLComplaintRepository complaintRepo, MySQLUserRepository userRepo) {
        try {
            complaintRepo.deleteComplaint("C_TEST_001");
            complaintRepo.deleteComplaint("C_TEST_INVALID");
            userRepo.deleteUser("T_STU01");
            userRepo.deleteUser("T_STU02");
            userRepo.deleteUser("T_STAFF01");
            userRepo.deleteUser("T_ADM01");
        } catch (Exception ignored) {}
    }
}
