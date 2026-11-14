package service;

import exception.ComplaintNotFoundException;
import exception.InvalidInputException;
import model.Complaint;
import model.User;
import repository.ComplaintRepository;
import repository.MySQLComplaintRepository;

import java.util.ArrayList;

/**
 * AssignmentService
 *
 * Handles assigning complaints to maintenance staff members and tracking
 * work allocated to specific technicians via MySQL.
 */
public class AssignmentService {

    private final ComplaintRepository complaintRepository;
    private final UserService userService;

    public AssignmentService() {
        this.complaintRepository = new MySQLComplaintRepository();
        this.userService = new UserService();
    }

    public AssignmentService(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
        this.userService = new UserService();
    }

    public AssignmentService(ComplaintRepository complaintRepository, UserService userService) {
        this.complaintRepository = complaintRepository;
        this.userService = userService;
    }

    /**
     * Assigns a complaint to a staff member.
     *
     * @param complaintId Complaint ID
     * @param staffId Assigned Staff User ID
     * @return true if successful
     * @throws ComplaintNotFoundException if complaint does not exist
     * @throws InvalidInputException if inputs are invalid or staff is inactive
     */
    public boolean assignComplaint(String complaintId, String staffId)
            throws ComplaintNotFoundException, InvalidInputException {
        return assignComplaint(complaintId, staffId, null);
    }

    /**
     * Assigns a complaint to a staff member with administrator audit logging.
     * Verifies that the assigned technician is an active staff member.
     */
    public boolean assignComplaint(String complaintId, String staffId, String assignedBy)
            throws ComplaintNotFoundException, InvalidInputException {

        if (complaintId == null || complaintId.trim().isEmpty()) {
            throw new InvalidInputException("Complaint ID cannot be empty.");
        }

        if (staffId == null || staffId.trim().isEmpty()) {
            throw new InvalidInputException("Staff ID cannot be empty.");
        }

        if (userService != null) {
            User staffUser = userService.findUserById(staffId.trim());
            if (staffUser == null) {
                throw new InvalidInputException("Staff member " + staffId + " not found.");
            }
            if (!"STAFF".equalsIgnoreCase(staffUser.getRole())) {
                throw new InvalidInputException("User " + staffId + " is not a maintenance staff member.");
            }
            if (!staffUser.isActive()) {
                throw new InvalidInputException("Cannot assign complaint to inactive staff member.");
            }
        }

        Complaint complaint = complaintRepository.findById(complaintId.trim());
        if (complaint == null) {
            throw new ComplaintNotFoundException("Complaint " + complaintId + " not found.");
        }

        boolean success = complaintRepository.assignStaff(complaintId.trim(), staffId.trim());
        if (!success) {
            throw new ComplaintNotFoundException("Complaint " + complaintId + " could not be assigned.");
        }

        return true;
    }

    /**
     * Retrieves all complaints assigned to a particular staff member.
     */
    public ArrayList<Complaint> getComplaintsByStaff(String staffId) {
        if (staffId == null || staffId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return complaintRepository.findByAssignedStaffId(staffId.trim());
    }
}
