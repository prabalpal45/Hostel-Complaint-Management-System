package database;

import model.Admin;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * DataSeeder
 *
 * Populates hostel_complaint_db with fresh demonstration data:
 * - 1 Admin (admin / Admin@123)
 * - 3 Students (student01, student02, student03 / Student@123)
 * - 3 Staff members (staff01, staff02, staff03 / Staff@123)
 * - 12 realistic complaints across Electrical, Plumbing, Cleaning, Furniture, Internet
 * - Corresponding complaint_history transitions for audit trail
 */
public class DataSeeder {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   HOSTEL SYSTEM - FRESH DEMO DATA SEEDER        ");
        System.out.println("=================================================");

        if (!DatabaseConnection.testConnection()) {
            System.err.println("Database connection failed. Please verify MySQL service and db.properties.");
            System.exit(1);
        }

        seedData();
    }

    public static void seedData() {
        MySQLUserRepository userRepo = new MySQLUserRepository();
        MySQLComplaintRepository complaintRepo = new MySQLComplaintRepository();

        // 1. Create and insert Users
        System.out.println("\n[1/3] Inserting Demo Users into MySQL...");
        List<User> users = new ArrayList<>();

        // Admin
        users.add(new Admin("ADM001", "Hostel Chief Warden", "admin", "Admin@123"));

        // Students
        users.add(new Student("S001", "Rahul Sharma", "student01", "Student@123", "B-204"));
        users.add(new Student("S002", "Ananya Verma", "student02", "Student@123", "C-102"));
        users.add(new Student("S003", "Rohan Gupta", "student03", "Student@123", "A-301"));

        // Maintenance Staff
        users.add(new Staff("ST001", "Ramesh Kumar", "staff01", "Staff@123", "Electrical"));
        users.add(new Staff("ST002", "Suresh Patel", "staff02", "Staff@123", "Plumbing"));
        users.add(new Staff("ST003", "Mahesh Singh", "staff03", "Staff@123", "Cleaning"));

        for (User u : users) {
            userRepo.saveUser(u);
            System.out.println("  -> Inserted " + u.getRole() + ": " + u.getUsername() + " (" + u.getUserId() + ") - " + u.getName());
        }

        // 2. Insert Complaints
        System.out.println("\n[2/3] Inserting 12 Demo Complaints into MySQL...");

        record DemoComplaint(
                String complaintId,
                String studentId,
                String roomNumber,
                String category,
                String description,
                ComplaintStatus status,
                ComplaintPriority priority,
                String assignedStaffId,
                String remarks,
                int hoursAgo
        ) {}

        List<DemoComplaint> complaints = List.of(
                new DemoComplaint("C001", "S001", "B-204", "Electrical",
                        "Ceiling fan speed regulator is completely non-functional, fan running only at maximum speed",
                        ComplaintStatus.RESOLVED, ComplaintPriority.HIGH, "ST001",
                        "Replaced rotary regulator switch and verified speeds 1 to 5.", 48),

                new DemoComplaint("C002", "S001", "B-204", "Plumbing",
                        "Bathroom washbasin pipe leaking water onto floor underneath cabinet",
                        ComplaintStatus.IN_PROGRESS, ComplaintPriority.MEDIUM, "ST002",
                        "Inspected joint. Replacement silicone washer ordered from store.", 24),

                new DemoComplaint("C003", "S001", "B-204", "Furniture",
                        "Study chair backrest screw has come loose and wobbles heavily",
                        ComplaintStatus.PENDING, ComplaintPriority.LOW, null,
                        "", 5),

                new DemoComplaint("C004", "S002", "C-102", "Electrical",
                        "Main tube light flickering continuously and making buzzing hum noise",
                        ComplaintStatus.RESOLVED, ComplaintPriority.MEDIUM, "ST001",
                        "Installed new 20W LED batten fixture. Tested OK.", 36),

                new DemoComplaint("C005", "S002", "C-102", "Cleaning",
                        "Balcony drain blocked by accumulated leaves, water pooling after rain",
                        ComplaintStatus.IN_PROGRESS, ComplaintPriority.HIGH, "ST003",
                        "Drain cleared with snake rod; disinfectant wash underway.", 12),

                new DemoComplaint("C006", "S002", "C-102", "Internet",
                        "Hostel Wi-Fi access point in C-block 1st floor drops packets every 5 minutes",
                        ComplaintStatus.ASSIGNED, ComplaintPriority.MEDIUM, "ST001",
                        "", 8),

                new DemoComplaint("C007", "S002", "C-102", "Plumbing",
                        "Overhead water tank line pressure very low in morning hours",
                        ComplaintStatus.PENDING, ComplaintPriority.LOW, null,
                        "", 4),

                new DemoComplaint("C008", "S003", "A-301", "Electrical",
                        "Wall power socket near bed sparking occasionally when laptop charger plugged in",
                        ComplaintStatus.ASSIGNED, ComplaintPriority.HIGH, "ST001",
                        "", 16),

                new DemoComplaint("C009", "S003", "A-301", "Plumbing",
                        "Shower head water valve stuck in shut position, cannot turn knob",
                        ComplaintStatus.RESOLVED, ComplaintPriority.HIGH, "ST002",
                        "De-scaled valve core with solvent and greased spindle. Working smoothly.", 30),

                new DemoComplaint("C010", "S003", "A-301", "Furniture",
                        "Wardrobe hinge loose, door misaligned and rubbing against floor",
                        ComplaintStatus.IN_PROGRESS, ComplaintPriority.MEDIUM, "ST003",
                        "Replaced worn wooden dowels and tightened mounting screws.", 18),

                new DemoComplaint("C011", "S003", "A-301", "Internet",
                        "LAN Ethernet wall port keystone jack loose, cable disconnects on touch",
                        ComplaintStatus.PENDING, ComplaintPriority.LOW, null,
                        "", 2),

                new DemoComplaint("C012", "S001", "B-204", "Cleaning",
                        "Corridor window pane broken during thunderstorm, glass shards need cleanup",
                        ComplaintStatus.RESOLVED, ComplaintPriority.HIGH, "ST003",
                        "Glass shards safely cleared, protective sheet taped over frame pending carpentry.", 40)
        );

        String insertSql = "INSERT INTO complaints (complaint_id, student_id, room_number, category, description, status, priority, assigned_staff_id, remarks, created_at, updated_at) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String historySql = "INSERT INTO complaint_history (complaint_id, old_status, new_status, updated_by, updated_at) "
                          + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psComp = conn.prepareStatement(insertSql);
                 PreparedStatement psHist = conn.prepareStatement(historySql)) {

                for (DemoComplaint c : complaints) {
                    Instant createdInstant = Instant.now().minus(c.hoursAgo(), ChronoUnit.HOURS);
                    Timestamp createdTs = Timestamp.from(createdInstant);
                    Timestamp updatedTs = Timestamp.from(Instant.now().minus(c.hoursAgo() / 2, ChronoUnit.HOURS));

                    psComp.setString(1, c.complaintId());
                    psComp.setString(2, c.studentId());
                    psComp.setString(3, c.roomNumber());
                    psComp.setString(4, c.category());
                    psComp.setString(5, c.description());
                    psComp.setString(6, c.status().name());
                    psComp.setString(7, c.priority().name());
                    psComp.setString(8, c.assignedStaffId());
                    psComp.setString(9, c.remarks());
                    psComp.setTimestamp(10, createdTs);
                    psComp.setTimestamp(11, updatedTs);
                    psComp.executeUpdate();

                    // Insert audit history based on status
                    // 1. PENDING (Initial lodge)
                    psHist.setString(1, c.complaintId());
                    psHist.setString(2, null);
                    psHist.setString(3, ComplaintStatus.PENDING.name());
                    psHist.setString(4, c.studentId());
                    psHist.setTimestamp(5, createdTs);
                    psHist.executeUpdate();

                    // 2. ASSIGNED
                    if (c.status() == ComplaintStatus.ASSIGNED || c.status() == ComplaintStatus.IN_PROGRESS || c.status() == ComplaintStatus.RESOLVED) {
                        psHist.setString(1, c.complaintId());
                        psHist.setString(2, ComplaintStatus.PENDING.name());
                        psHist.setString(3, ComplaintStatus.ASSIGNED.name());
                        psHist.setString(4, "ADM001");
                        psHist.setTimestamp(5, Timestamp.from(createdInstant.plus(1, ChronoUnit.HOURS)));
                        psHist.executeUpdate();
                    }

                    // 3. IN_PROGRESS
                    if (c.status() == ComplaintStatus.IN_PROGRESS || c.status() == ComplaintStatus.RESOLVED) {
                        psHist.setString(1, c.complaintId());
                        psHist.setString(2, ComplaintStatus.ASSIGNED.name());
                        psHist.setString(3, ComplaintStatus.IN_PROGRESS.name());
                        psHist.setString(4, c.assignedStaffId());
                        psHist.setTimestamp(5, Timestamp.from(createdInstant.plus(3, ChronoUnit.HOURS)));
                        psHist.executeUpdate();
                    }

                    // 4. RESOLVED
                    if (c.status() == ComplaintStatus.RESOLVED) {
                        psHist.setString(1, c.complaintId());
                        psHist.setString(2, ComplaintStatus.IN_PROGRESS.name());
                        psHist.setString(3, ComplaintStatus.RESOLVED.name());
                        psHist.setString(4, c.assignedStaffId());
                        psHist.setTimestamp(5, updatedTs);
                        psHist.executeUpdate();
                    }

                    System.out.println("  -> Inserted Complaint " + c.complaintId() + " (" + c.category() + " | " + c.priority() + " | " + c.status() + ") -> Student " + c.studentId());
                }

                conn.commit();
                System.out.println("\n[3/3] Demo Complaints and Audit History successfully committed to MySQL.");

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("Error seeding demo complaints: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=================================================");
        System.out.println("   DEMO DATA SEEDING COMPLETE!                   ");
        System.out.println("=================================================");
    }
}
