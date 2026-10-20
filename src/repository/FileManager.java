package repository;

import model.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class FileManager implements UserRepository {

    private static final String USER_FILE = "data/users.txt";
    private static final String COMPLAINT_FILE = "data/complaints.txt";

    private String escapeField(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    private String unescapeField(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\\\", "\\");
    }

    private void ensureDir(String path) {
        File file = new File(path);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
    }

    public void saveUser(User user) {

        ensureDir(USER_FILE);

        String extraInformation = "-";

        if (user instanceof Student) {

            Student student = (Student) user;
            extraInformation = student.getRoomNumber();

        } else if (user instanceof Staff) {

            Staff staff = (Staff) user;
            extraInformation = staff.getDepartment();
        }

        try (BufferedWriter writer =
                     new BufferedWriter(
                             new FileWriter(USER_FILE, true))) {

            writer.write(
                    escapeField(user.getUserId()) + "," +
                            escapeField(user.getName()) + "," +
                            escapeField(user.getUsername()) + "," +
                            escapeField(user.getPassword()) + "," +
                            escapeField(user.getRole()) + "," +
                            escapeField(extraInformation)
            );

            writer.newLine();

        } catch (IOException e) {

            System.out.println("Error saving user.");
            e.printStackTrace();
        }
    }

    public ArrayList<User> loadUsers() {

        ArrayList<User> users = new ArrayList<>();
        File file = new File(USER_FILE);
        if (!file.exists()) {
            return users;
        }

        try (BufferedReader reader =
                     new BufferedReader(
                             new FileReader(file))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] data = line.split("(?<!\\\\),", -1);

                if (data.length < 6) {
                    continue;
                }

                String userId = unescapeField(data[0].trim());
                String name = unescapeField(data[1].trim());
                String username = unescapeField(data[2].trim());
                String password = unescapeField(data[3]);
                String role = unescapeField(data[4].trim());
                String extraInformation = unescapeField(data[5].trim());

                if (role.equals("STUDENT")) {

                    Student student = new Student(
                            userId,
                            name,
                            username,
                            password,
                            extraInformation
                    );

                    users.add(student);

                } else if (role.equals("ADMIN")) {

                    Admin admin = new Admin(
                            userId,
                            name,
                            username,
                            password
                    );

                    users.add(admin);

                } else if (role.equals("STAFF")) {

                    Staff staff = new Staff(
                            userId,
                            name,
                            username,
                            password,
                            extraInformation
                    );

                    users.add(staff);
                }
            }

        } catch (IOException e) {

            System.out.println("Error reading users.");
            e.printStackTrace();
        }

        return users;
    }

    public void saveComplaint(Complaint complaint) {

        ensureDir(COMPLAINT_FILE);

        try (BufferedWriter writer =
                     new BufferedWriter(
                             new FileWriter(COMPLAINT_FILE, true))) {

            writer.write(
                    escapeField(complaint.getComplaintId()) + "," +
                            escapeField(complaint.getStudentId()) + "," +
                            escapeField(complaint.getRoomNumber()) + "," +
                            escapeField(complaint.getCategory()) + "," +
                            escapeField(complaint.getDescription()) + "," +
                            complaint.getStatus() + "," +
                            complaint.getPriority() + "," +
                            escapeField(complaint.getAssignedStaffId()) + "," +
                            escapeField(complaint.getRemarks())
            );

            writer.newLine();

        } catch (IOException e) {

            System.out.println("Error saving complaint.");
            e.printStackTrace();
        }
    }

    public ArrayList<Complaint> loadComplaints() {

        ArrayList<Complaint> complaints = new ArrayList<>();
        File file = new File(COMPLAINT_FILE);
        if (!file.exists()) {
            return complaints;
        }

        try (BufferedReader reader =
                     new BufferedReader(
                             new FileReader(file))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] data = line.split("(?<!\\\\),", -1);

                if (data.length < 9) {
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

                    status = ComplaintStatus.valueOf(data[5].trim());
                    priority = ComplaintPriority.valueOf(data[6].trim());

                } catch (IllegalArgumentException e) {

                    System.out.println(
                            "Invalid complaint status or priority in file: " + line
                    );

                    continue;
                }

                String assignedStaffId = unescapeField(data[7].trim());
                String remarks = unescapeField(data[8]);

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

                complaints.add(complaint);
            }

        } catch (IOException e) {

            System.out.println("Error reading complaints.");
            e.printStackTrace();
        }

        return complaints;
    }

    public void saveAllComplaints(ArrayList<Complaint> complaints) {

        ensureDir(COMPLAINT_FILE);

        try (BufferedWriter writer =
                     new BufferedWriter(
                             new FileWriter(COMPLAINT_FILE))) {

            for (Complaint complaint : complaints) {

                writer.write(
                        escapeField(complaint.getComplaintId()) + "," +
                                escapeField(complaint.getStudentId()) + "," +
                                escapeField(complaint.getRoomNumber()) + "," +
                                escapeField(complaint.getCategory()) + "," +
                                escapeField(complaint.getDescription()) + "," +
                                complaint.getStatus() + "," +
                                complaint.getPriority() + "," +
                                escapeField(complaint.getAssignedStaffId()) + "," +
                                escapeField(complaint.getRemarks())
                );

                writer.newLine();
            }

        } catch (IOException e) {

            System.out.println("Error saving complaints.");
            e.printStackTrace();
        }
    }
}

