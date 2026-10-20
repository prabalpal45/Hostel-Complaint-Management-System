package service;

import model.Complaint;
import model.ComplaintStatus;
import repository.FileManager;
import exception.ComplaintNotFoundException;
import exception.InvalidInputException;
import java.util.ArrayList;

public class ComplaintService {

    private FileManager fileManager;

    public ComplaintService() {
        fileManager = new FileManager();
    }

    public void createComplaint(Complaint complaint)
            throws InvalidInputException {

        if (complaint.getComplaintId() == null ||
                complaint.getComplaintId().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Complaint ID cannot be empty."
            );
        }

        if (complaint.getStudentId() == null ||
                complaint.getStudentId().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Student ID cannot be empty."
            );
        }

        if (complaint.getRoomNumber() == null ||
                complaint.getRoomNumber().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Room number cannot be empty."
            );
        }

        if (complaint.getCategory() == null ||
                complaint.getCategory().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Category cannot be empty."
            );
        }

        if (complaint.getDescription() == null ||
                complaint.getDescription().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Complaint description cannot be empty."
            );
        }
        if (getAllComplaints().stream()
                .anyMatch(existingComplaint ->
                        existingComplaint.getComplaintId()
                                .equals(complaint.getComplaintId()))) {

            throw new InvalidInputException(
                    "Complaint ID already exists."
            );
        }

        fileManager.saveComplaint(complaint);

        System.out.println(
                "Complaint created successfully."
        );
    }

    public ArrayList<Complaint> getAllComplaints() {

        return fileManager.loadComplaints();
    }

    public ArrayList<Complaint> getComplaintsByStudent(String studentId) {

        ArrayList<Complaint> allComplaints =
                fileManager.loadComplaints();

        ArrayList<Complaint> studentComplaints =
                new ArrayList<>();

        for (Complaint complaint : allComplaints) {

            if (complaint.getStudentId().equalsIgnoreCase(studentId.trim())) {

                studentComplaints.add(complaint);
            }
        }

        return studentComplaints;
    }

    public Complaint getComplaintById(String complaintId)
            throws ComplaintNotFoundException {

        if (complaintId == null || complaintId.trim().isEmpty()) {
            throw new ComplaintNotFoundException("Complaint ID cannot be empty.");
        }

        for (Complaint complaint : fileManager.loadComplaints()) {

            if (complaint.getComplaintId().equalsIgnoreCase(complaintId.trim())) {
                return complaint;
            }
        }

        throw new ComplaintNotFoundException(
                "Complaint " + complaintId + " not found."
        );
    }

    public boolean updateStatus(String complaintId, ComplaintStatus newStatus)
            throws ComplaintNotFoundException,
            InvalidInputException {
        if (complaintId == null ||
                complaintId.trim().isEmpty()) {

            throw new InvalidInputException(
                    "Complaint ID cannot be empty."
            );
        }
        if (newStatus == null) {

            throw new InvalidInputException(
                    "Complaint status cannot be null."
            );
        }

        ArrayList<Complaint> complaints =
                fileManager.loadComplaints();

        for (Complaint complaint : complaints) {

            if (complaint.getComplaintId().equals(complaintId)) {

                complaint.setStatus(newStatus);

                fileManager.saveAllComplaints(complaints);

                return true;
            }
        }

        throw new ComplaintNotFoundException(
                "Complaint " + complaintId + " not found."
        );
    }


    public boolean addRemarks(
            String complaintId,
            String remarks)
            throws ComplaintNotFoundException,
            InvalidInputException {

            if (remarks == null ||
                    remarks.trim().isEmpty()) {

                throw new InvalidInputException(
                        "Remarks cannot be empty."
                );
            }
            ArrayList<Complaint> complaints =
                    fileManager.loadComplaints();

            for (Complaint complaint : complaints) {

                if (complaint.getComplaintId().equals(complaintId)) {

                    complaint.setRemarks(remarks);

                    fileManager.saveAllComplaints(complaints);

                    return true;
                }
            }

            throw new ComplaintNotFoundException(
                    "Complaint " + complaintId + " not found."
            );
        }
    }
