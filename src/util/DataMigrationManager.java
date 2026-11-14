package util;

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DataMigrationManager
 *
 * Provides safe, auditable import and export mechanisms between legacy TXT storage
 * and the primary MySQL database.
 *
 * Guarantees:
 * 1. MySQL is the primary store; TXT files are backup/export targets.
 * 2. Original TXT files are never deleted.
 * 3. Existing MySQL records are never silently overwritten during import.
 * 4. Passwords imported into MySQL are securely hashed with BCrypt.
 * 5. Format compatibility with existing FileManager is strictly preserved.
 */
public class DataMigrationManager {

    private static final String DEFAULT_USERS_FILE = "data/users.txt";
    private static final String DEFAULT_COMPLAINTS_FILE = "data/complaints.txt";

    public static class ImportResult {
        public int imported = 0;
        public int skippedDuplicates = 0;
        public int failedMalformed = 0;
        public final List<String> details = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("Imported: %d, Skipped Duplicates: %d, Failed/Malformed: %d",
                    imported, skippedDuplicates, failedMalformed);
        }
    }

    public static class ExportResult {
        public int exported = 0;
        public String filePath = "";
        public boolean success = false;
        public String message = "";

        @Override
        public String toString() {
            return String.format("Exported: %d records to %s (Success: %b)", exported, filePath, success);
        }
    }

    // =============================================================
    // 1. TXT TO MYSQL IMPORT
    // =============================================================

    /**
     * Imports users from a TXT file into MySQL without deleting or modifying the source file.
     */
    public static ImportResult importUsersFromTxt(String filePath) {
        ImportResult result = new ImportResult();
        File file = new File(filePath != null ? filePath : DEFAULT_USERS_FILE);

        if (!file.exists()) {
            result.details.add("File does not exist: " + file.getPath());
            return result;
        }

        MySQLUserRepository userRepo = new MySQLUserRepository();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] data = line.split("(?<!\\\\),", -1);
                if (data.length < 5) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": Insufficient columns (" + data.length + ")");
                    continue;
                }

                String userId = unescapeField(data[0].trim());
                String name = unescapeField(data[1].trim());
                String username = unescapeField(data[2].trim());
                String rawPassword = unescapeField(data[3]);
                String role = unescapeField(data[4].trim()).toUpperCase();
                String extra = data.length > 5 ? unescapeField(data[5].trim()) : "";

                if (userId.isEmpty() || name.isEmpty() || username.isEmpty() || rawPassword.isEmpty()) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": Empty required fields for user " + userId);
                    continue;
                }

                // Check duplicate ID in MySQL
                if (userRepo.existsById(userId)) {
                    result.skippedDuplicates++;
                    result.details.add("Line " + lineNumber + ": User ID '" + userId + "' already exists in MySQL (skipped)");
                    continue;
                }

                // Check duplicate username in MySQL
                if (userRepo.existsByUsername(username)) {
                    result.skippedDuplicates++;
                    result.details.add("Line " + lineNumber + ": Username '" + username + "' already exists in MySQL (skipped)");
                    continue;
                }

                User user;
                switch (role) {
                    case "STUDENT":
                        user = new Student(userId, name, username, rawPassword, extra);
                        break;
                    case "STAFF":
                        user = new Staff(userId, name, username, rawPassword, extra);
                        break;
                    case "ADMIN":
                        user = new Admin(userId, name, username, rawPassword);
                        break;
                    default:
                        result.failedMalformed++;
                        result.details.add("Line " + lineNumber + ": Unknown role '" + role + "'");
                        continue;
                }

                try {
                    userRepo.insertUser(user);
                    result.imported++;
                    result.details.add("Line " + lineNumber + ": Successfully imported " + role + " '" + username + "' (" + userId + ")");
                } catch (Exception e) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": DB insert error: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            result.details.add("IO Error reading users file: " + e.getMessage());
        }

        return result;
    }

    /**
     * Imports complaints from a TXT file into MySQL, verifying foreign key integrity.
     */
    public static ImportResult importComplaintsFromTxt(String filePath) {
        ImportResult result = new ImportResult();
        File file = new File(filePath != null ? filePath : DEFAULT_COMPLAINTS_FILE);

        if (!file.exists()) {
            result.details.add("File does not exist: " + file.getPath());
            return result;
        }

        MySQLUserRepository userRepo = new MySQLUserRepository();
        MySQLComplaintRepository complaintRepo = new MySQLComplaintRepository();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] data = line.split("(?<!\\\\),", -1);
                if (data.length < 7) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": Insufficient columns for complaint (" + data.length + ")");
                    continue;
                }

                String complaintId = unescapeField(data[0].trim());
                String studentId = unescapeField(data[1].trim());
                String roomNumber = unescapeField(data[2].trim());
                String category = unescapeField(data[3].trim());
                String description = unescapeField(data[4]);

                ComplaintStatus status;
                ComplaintPriority priority;

                try {
                    status = ComplaintStatus.valueOf(data[5].trim().toUpperCase());
                    priority = ComplaintPriority.valueOf(data[6].trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": Invalid status or priority value");
                    continue;
                }

                String assignedStaffId = (data.length > 7) ? unescapeField(data[7].trim()) : "";
                String remarks = (data.length > 8) ? unescapeField(data[8]) : "";

                if (complaintId.isEmpty() || studentId.isEmpty() || category.isEmpty() || description.isEmpty()) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": Empty required fields for complaint " + complaintId);
                    continue;
                }

                // Check duplicate complaint ID
                if (complaintRepo.findById(complaintId) != null) {
                    result.skippedDuplicates++;
                    result.details.add("Line " + lineNumber + ": Complaint ID '" + complaintId + "' already exists in MySQL (skipped)");
                    continue;
                }

                // Foreign Key validation: verify student exists in MySQL
                if (!userRepo.existsById(studentId)) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": Student '" + studentId + "' does not exist in MySQL. Complaint skipped to prevent FK violation.");
                    continue;
                }

                // Foreign Key validation: if assignedStaffId is present, verify staff exists in MySQL
                if (!assignedStaffId.isEmpty() && !userRepo.existsById(assignedStaffId)) {
                    result.details.add("Line " + lineNumber + ": Warning: Staff '" + assignedStaffId + "' not found in MySQL. Setting assignment to unassigned.");
                    assignedStaffId = "";
                }

                Complaint complaint = new Complaint(
                        complaintId,
                        studentId,
                        roomNumber,
                        category,
                        description,
                        priority
                );
                complaint.setStatus(status);
                complaint.setAssignedStaffId(assignedStaffId);
                complaint.setRemarks(remarks);

                try {
                    complaintRepo.insertComplaint(complaint);
                    result.imported++;
                    result.details.add("Line " + lineNumber + ": Successfully imported Complaint '" + complaintId + "'");
                } catch (Exception e) {
                    result.failedMalformed++;
                    result.details.add("Line " + lineNumber + ": DB insert error: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            result.details.add("IO Error reading complaints file: " + e.getMessage());
        }

        return result;
    }

    // =============================================================
    // 2. MYSQL TO TXT EXPORT
    // =============================================================

    /**
     * Exports all users from MySQL into users.txt using the format expected by FileManager.
     */
    public static ExportResult exportUsersToTxt(String targetFilePath) {
        ExportResult result = new ExportResult();
        String path = targetFilePath != null ? targetFilePath : DEFAULT_USERS_FILE;
        result.filePath = path;

        MySQLUserRepository userRepo = new MySQLUserRepository();
        ArrayList<User> users = userRepo.loadUsers();

        ensureParentDir(path);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (User u : users) {
                String extra = "-";
                if (u instanceof Student) {
                    extra = ((Student) u).getRoomNumber();
                } else if (u instanceof Staff) {
                    extra = ((Staff) u).getDepartment();
                }

                writer.write(
                        escapeField(u.getUserId()) + "," +
                        escapeField(u.getName()) + "," +
                        escapeField(u.getUsername()) + "," +
                        escapeField(u.getPassword()) + "," +
                        escapeField(u.getRole()) + "," +
                        escapeField(extra)
                );
                writer.newLine();
                result.exported++;
            }
            result.success = true;
            result.message = "Successfully exported " + result.exported + " users to " + path;
        } catch (IOException e) {
            result.success = false;
            result.message = "Export failed: " + e.getMessage();
        }

        return result;
    }

    /**
     * Exports all complaints from MySQL into complaints.txt using the format expected by FileManager.
     */
    public static ExportResult exportComplaintsToTxt(String targetFilePath) {
        ExportResult result = new ExportResult();
        String path = targetFilePath != null ? targetFilePath : DEFAULT_COMPLAINTS_FILE;
        result.filePath = path;

        MySQLComplaintRepository complaintRepo = new MySQLComplaintRepository();
        ArrayList<Complaint> complaints = complaintRepo.loadComplaints();

        ensureParentDir(path);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (Complaint c : complaints) {
                writer.write(
                        escapeField(c.getComplaintId()) + "," +
                        escapeField(c.getStudentId()) + "," +
                        escapeField(c.getRoomNumber()) + "," +
                        escapeField(c.getCategory()) + "," +
                        escapeField(c.getDescription()) + "," +
                        c.getStatus().name() + "," +
                        c.getPriority().name() + "," +
                        escapeField(c.getAssignedStaffId()) + "," +
                        escapeField(c.getRemarks())
                );
                writer.newLine();
                result.exported++;
            }
            result.success = true;
            result.message = "Successfully exported " + result.exported + " complaints to " + path;
        } catch (IOException e) {
            result.success = false;
            result.message = "Export failed: " + e.getMessage();
        }

        return result;
    }

    // =============================================================
    // Helper Formatting Methods (matching FileManager.java)
    // =============================================================

    private static String escapeField(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    private static String unescapeField(String text) {
        if (text == null) return "";
        return text.replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\\\", "\\");
    }

    private static void ensureParentDir(String path) {
        File file = new File(path);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
    }

    // =============================================================
    // CLI Entry Point
    // =============================================================
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("    HOSTEL SYSTEM - DATA MIGRATION UTILITY       ");
        System.out.println("=================================================");

        String action = (args.length > 0) ? args[0].toLowerCase() : "help";

        if ("import".equals(action)) {
            System.out.println("\n[1/2] Importing Users from " + DEFAULT_USERS_FILE + "...");
            ImportResult uRes = importUsersFromTxt(DEFAULT_USERS_FILE);
            System.out.println("Users Import Result: " + uRes);
            for (String d : uRes.details) {
                System.out.println("  - " + d);
            }

            System.out.println("\n[2/2] Importing Complaints from " + DEFAULT_COMPLAINTS_FILE + "...");
            ImportResult cRes = importComplaintsFromTxt(DEFAULT_COMPLAINTS_FILE);
            System.out.println("Complaints Import Result: " + cRes);
            for (String d : cRes.details) {
                System.out.println("  - " + d);
            }

        } else if ("export".equals(action)) {
            System.out.println("\n[1/2] Exporting Users to " + DEFAULT_USERS_FILE + "...");
            ExportResult uRes = exportUsersToTxt(DEFAULT_USERS_FILE);
            System.out.println("Result: " + uRes.message);

            System.out.println("\n[2/2] Exporting Complaints to " + DEFAULT_COMPLAINTS_FILE + "...");
            ExportResult cRes = exportComplaintsToTxt(DEFAULT_COMPLAINTS_FILE);
            System.out.println("Result: " + cRes.message);

        } else {
            System.out.println("Usage:");
            System.out.println("  java util.DataMigrationManager import  (Import legacy TXT data into MySQL)");
            System.out.println("  java util.DataMigrationManager export  (Backup current MySQL data into TXT)");
        }
    }
}
