package service;

import exception.ComplaintNotFoundException;
import exception.InvalidInputException;
import model.Complaint;
import model.ComplaintStatus;
import repository.ComplaintRepository;
import repository.MySQLComplaintRepository;

import java.util.ArrayList;

/**
 * ComplaintService
 *
 * Business logic service managing complaint lodging, retrieval, status updates,
 * and technician remarks. Uses MySQL as the primary persistence engine.
 */
public class ComplaintService {

    private final ComplaintRepository complaintRepository;

    public ComplaintService() {
        this.complaintRepository = new MySQLComplaintRepository();
    }

    public ComplaintService(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    /**
     * Creates and lodges a new complaint in MySQL.
     *
     * @param complaint Complaint to lodge
     * @throws InvalidInputException if required fields are missing or ID already exists
     */
    public void createComplaint(Complaint complaint) throws InvalidInputException {
        if (complaint == null) {
            throw new InvalidInputException("Complaint cannot be null.");
        }

        if (complaint.getComplaintId() == null || complaint.getComplaintId().trim().isEmpty()) {
            throw new InvalidInputException("Complaint ID cannot be empty.");
        }

        if (complaint.getStudentId() == null || complaint.getStudentId().trim().isEmpty()) {
            throw new InvalidInputException("Student ID cannot be empty.");
        }

        if (complaint.getRoomNumber() == null || complaint.getRoomNumber().trim().isEmpty()) {
            throw new InvalidInputException("Room number cannot be empty.");
        }

        if (complaint.getCategory() == null || complaint.getCategory().trim().isEmpty()) {
            throw new InvalidInputException("Category cannot be empty.");
        }

        if (complaint.getDescription() == null || complaint.getDescription().trim().isEmpty()) {
            throw new InvalidInputException("Complaint description cannot be empty.");
        }

        if (complaint.getPriority() == null) {
            throw new InvalidInputException("Complaint priority cannot be null.");
        }

        // Duplicate check using primary persistence
        if (complaintRepository.findById(complaint.getComplaintId().trim()) != null) {
            throw new InvalidInputException("Complaint ID already exists.");
        }

        complaintRepository.saveComplaint(complaint);
        System.out.println("Complaint " + complaint.getComplaintId() + " created successfully.");
    }

    /**
     * Retrieves all complaints.
     */
    public ArrayList<Complaint> getAllComplaints() {
        return complaintRepository.loadComplaints();
    }

    /**
     * Retrieves complaints lodged by a specific student.
     */
    public ArrayList<Complaint> getComplaintsByStudent(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return complaintRepository.findByStudentId(studentId.trim());
    }

    /**
     * Retrieves a single complaint by ID.
     *
     * @throws ComplaintNotFoundException if not found
     */
    public Complaint getComplaintById(String complaintId) throws ComplaintNotFoundException {
        if (complaintId == null || complaintId.trim().isEmpty()) {
            throw new ComplaintNotFoundException("Complaint ID cannot be empty.");
        }

        Complaint complaint = complaintRepository.findById(complaintId.trim());
        if (complaint == null) {
            throw new ComplaintNotFoundException("Complaint " + complaintId + " not found.");
        }

        return complaint;
    }

    /**
     * Updates complaint status.
     */
    public boolean updateStatus(String complaintId, ComplaintStatus newStatus)
            throws ComplaintNotFoundException, InvalidInputException {
        return updateStatus(complaintId, newStatus, null);
    }

    /**
     * Updates complaint status with an audit attribution.
     */
    public boolean updateStatus(String complaintId, ComplaintStatus newStatus, String updatedBy)
            throws ComplaintNotFoundException, InvalidInputException {

        if (complaintId == null || complaintId.trim().isEmpty()) {
            throw new InvalidInputException("Complaint ID cannot be empty.");
        }

        if (newStatus == null) {
            throw new InvalidInputException("Complaint status cannot be null.");
        }

        // Ensure complaint exists first
        getComplaintById(complaintId.trim());

        boolean updated = complaintRepository.updateStatus(complaintId.trim(), newStatus, updatedBy);
        if (!updated) {
            throw new ComplaintNotFoundException("Complaint " + complaintId + " could not be updated.");
        }

        return true;
    }

    /**
     * Adds staff/admin remarks to a complaint.
     */
    public boolean addRemarks(String complaintId, String remarks)
            throws ComplaintNotFoundException, InvalidInputException {

        if (complaintId == null || complaintId.trim().isEmpty()) {
            throw new InvalidInputException("Complaint ID cannot be empty.");
        }

        if (remarks == null || remarks.trim().isEmpty()) {
            throw new InvalidInputException("Remarks cannot be empty.");
        }

        // Ensure complaint exists first
        getComplaintById(complaintId.trim());

        boolean updated = complaintRepository.addRemarks(complaintId.trim(), remarks.trim());
        if (!updated) {
            throw new ComplaintNotFoundException("Complaint " + complaintId + " not found.");
        }

        return true;
    }
}
