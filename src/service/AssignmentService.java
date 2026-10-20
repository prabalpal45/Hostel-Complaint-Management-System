package service;

import model.Complaint;
import model.ComplaintStatus;
import repository.FileManager;
import exception.ComplaintNotFoundException;
import exception.InvalidInputException;

import java.util.ArrayList;

public class AssignmentService {

    private FileManager fileManager;

    public AssignmentService() {
        fileManager = new FileManager();
    }

    public boolean assignComplaint(
            String complaintId,
            String staffId)
            throws ComplaintNotFoundException,
            InvalidInputException {

        if (staffId == null ||
                staffId.trim().isEmpty()) {

            throw new InvalidInputException(
                    "Staff ID cannot be empty."
            );
        }

        ArrayList<Complaint> complaints =
                fileManager.loadComplaints();

        for (Complaint complaint : complaints) {

            if (complaint.getComplaintId().equals(complaintId)) {

                complaint.setAssignedStaffId(staffId);
                complaint.setStatus(ComplaintStatus.ASSIGNED);
                fileManager.saveAllComplaints(complaints);

                return true;
            }
        }

        throw new ComplaintNotFoundException(
                "Complaint " + complaintId + " not found."
        );
    }
    public ArrayList<Complaint> getComplaintsByStaff(String staffId) {

        ArrayList<Complaint> allComplaints =
                fileManager.loadComplaints();

        ArrayList<Complaint> staffComplaints =
                new ArrayList<>();

        if (staffId == null) {
            return staffComplaints;
        }

        for (Complaint complaint : allComplaints) {

            if (complaint.getAssignedStaffId() != null &&
                    complaint.getAssignedStaffId().equalsIgnoreCase(staffId.trim())) {

                staffComplaints.add(complaint);
            }
        }

        return staffComplaints;
    }
}