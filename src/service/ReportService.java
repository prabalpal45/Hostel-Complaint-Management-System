package service;

import model.Complaint;
import model.ComplaintPriority;
import model.ComplaintStatus;
import repository.ComplaintRepository;
import repository.MySQLComplaintRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportService
 *
 * Generates aggregated metrics and statistics for complaints.
 * Retrieves fast aggregated metrics from MySQL, while preserving Java Streams
 * and Lambdas demonstrations.
 */
public class ReportService {

    private final ComplaintRepository complaintRepository;

    public ReportService() {
        this.complaintRepository = new MySQLComplaintRepository();
    }

    public ReportService(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    /**
     * Gets the total number of complaints logged in MySQL.
     */
    public int getTotalComplaints() {
        return complaintRepository.getTotalCount();
    }

    /**
     * Gets count of complaints matching a status via MySQL query.
     */
    public int getComplaintsByStatus(ComplaintStatus status) {
        if (status == null) return 0;
        return complaintRepository.getCountByStatus(status);
    }

    /**
     * Demonstrates Java Streams and Lambda calculation for complaints by status.
     */
    public int getComplaintsByStatusUsingStream(ComplaintStatus status) {
        if (status == null) return 0;
        ArrayList<Complaint> complaints = complaintRepository.loadComplaints();
        return (int) complaints.stream()
                .filter(c -> c.getStatus() == status)
                .count();
    }

    /**
     * Gets count of complaints matching a priority via MySQL query.
     */
    public int getComplaintsByPriority(ComplaintPriority priority) {
        if (priority == null) return 0;
        return complaintRepository.getCountByPriority(priority);
    }

    /**
     * Demonstrates Java Streams and Lambda calculation for complaints by priority.
     */
    public int getComplaintsByPriorityUsingStream(ComplaintPriority priority) {
        if (priority == null) return 0;
        ArrayList<Complaint> complaints = complaintRepository.loadComplaints();
        return (int) complaints.stream()
                .filter(c -> c.getPriority() == priority)
                .count();
    }

    /**
     * Retrieves complaint breakdown by category.
     */
    public Map<String, Integer> getCategoryCounts() {
        return complaintRepository.getCategoryCounts();
    }

    /**
     * Generates and prints a complete report asynchronously using a background thread,
     * demonstrating concurrency and stream processing.
     */
    public void generateReportInBackground() {
        Thread reportThread = new Thread(() -> {
            System.out.println("\n[ReportService] Background report generation started...");

            int total = getTotalComplaints();
            int pending = getComplaintsByStatus(ComplaintStatus.PENDING);
            int assigned = getComplaintsByStatus(ComplaintStatus.ASSIGNED);
            int inProgress = getComplaintsByStatus(ComplaintStatus.IN_PROGRESS);
            int resolved = getComplaintsByStatus(ComplaintStatus.RESOLVED);

            System.out.println("===== COMPLAINT REPORT (MySQL) =====");
            System.out.println("Total Complaints : " + total);
            System.out.println("Pending          : " + pending);
            System.out.println("Assigned         : " + assigned);
            System.out.println("In Progress      : " + inProgress);
            System.out.println("Resolved         : " + resolved);
            System.out.println("====================================");
            System.out.println("[ReportService] Background report generation completed.\n");
        });

        reportThread.setName("Report-Worker-Thread");
        reportThread.start();
    }
}
