package service;

import model.Complaint;
import model.ComplaintStatus;
import repository.FileManager;

import java.util.ArrayList;

public class ReportService {

    private FileManager fileManager;

    public ReportService() {
        fileManager = new FileManager();
    }

    public int getTotalComplaints() {

        ArrayList<Complaint> complaints =
                fileManager.loadComplaints();

        return complaints.size();
    }

    public int getComplaintsByStatus(ComplaintStatus status) {

        ArrayList<Complaint> complaints =
                fileManager.loadComplaints();

        return (int) complaints.stream()
                .filter(complaint ->
                        complaint.getStatus() == status)
                .count();
    }

    public int getComplaintsByPriority(model.ComplaintPriority priority) {

        ArrayList<Complaint> complaints =
                fileManager.loadComplaints();

        return (int) complaints.stream()
                .filter(complaint ->
                        complaint.getPriority() == priority)
                .count();
    }

    public java.util.Map<String, Integer> getCategoryCounts() {

        ArrayList<Complaint> complaints =
                fileManager.loadComplaints();

        java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        for (Complaint c : complaints) {
            String cat = c.getCategory();
            if (cat == null || cat.trim().isEmpty()) {
                cat = "Other";
            }
            counts.put(cat, counts.getOrDefault(cat, 0) + 1);
        }

        return counts;
    }
    public void generateReportInBackground() {

        Thread reportThread = new Thread(() -> {

            System.out.println(
                    "Report generation started..."
            );

            int total = getTotalComplaints();

            int pending =
                    getComplaintsByStatus(
                            ComplaintStatus.PENDING
                    );

            int assigned =
                    getComplaintsByStatus(
                            ComplaintStatus.ASSIGNED
                    );

            int inProgress =
                    getComplaintsByStatus(
                            ComplaintStatus.IN_PROGRESS
                    );

            int resolved =
                    getComplaintsByStatus(
                            ComplaintStatus.RESOLVED
                    );

            System.out.println(
                    "\n===== COMPLAINT REPORT ====="
            );

            System.out.println(
                    "Total Complaints: " + total
            );

            System.out.println(
                    "Pending: " + pending
            );

            System.out.println(
                    "Assigned: " + assigned
            );

            System.out.println(
                    "In Progress: " + inProgress
            );

            System.out.println(
                    "Resolved: " + resolved
            );

            System.out.println(
                    "============================"
            );

            System.out.println(
                    "Report generation completed."
            );
        });

        reportThread.start();
    }
}