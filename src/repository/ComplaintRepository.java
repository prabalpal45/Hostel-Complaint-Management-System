package repository;

import model.Complaint;
import model.ComplaintPriority;
import model.ComplaintStatus;

import java.util.ArrayList;
import java.util.Map;

/**
 * ComplaintRepository
 *
 * Repository interface for complaint persistence and query operations.
 * Allows switching or complementing implementations (MySQL, File, etc.)
 * while maintaining clean architectural abstraction.
 */
public interface ComplaintRepository {

    void saveComplaint(Complaint complaint);

    ArrayList<Complaint> loadComplaints();

    Complaint findById(String complaintId);

    ArrayList<Complaint> findByStudentId(String studentId);

    ArrayList<Complaint> findByAssignedStaffId(String staffId);

    boolean updateStatus(String complaintId, ComplaintStatus newStatus, String updatedBy);

    boolean assignStaff(String complaintId, String staffId);

    boolean addRemarks(String complaintId, String remarks);

    boolean deleteComplaint(String complaintId);

    int getTotalCount();

    int getCountByStatus(ComplaintStatus status);

    int getCountByPriority(ComplaintPriority priority);

    Map<String, Integer> getCategoryCounts();
}
