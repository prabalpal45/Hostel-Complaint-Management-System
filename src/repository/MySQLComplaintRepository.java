package repository;

import database.DatabaseConnection;
import model.Complaint;
import model.ComplaintPriority;
import model.ComplaintStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * MySQLComplaintRepository
 *
 * Database-backed repository for Complaints and Complaint History audit trail.
 * Uses PreparedStatements, handles transactions safely, and preserves all
 * business model enums and constraints.
 */
public class MySQLComplaintRepository implements ComplaintRepository {

    @Override
    public void saveComplaint(Complaint complaint) {
        if (complaint == null) return;

        String assignedStaff = (complaint.getAssignedStaffId() != null && !complaint.getAssignedStaffId().trim().isEmpty())
                ? complaint.getAssignedStaffId().trim()
                : null;

        String remarks = (complaint.getRemarks() != null) ? complaint.getRemarks().trim() : "";

        String sql = "INSERT INTO complaints (complaint_id, student_id, room_number, category, description, status, priority, assigned_staff_id, remarks) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE "
                   + "room_number = VALUES(room_number), "
                   + "category = VALUES(category), "
                   + "description = VALUES(description), "
                   + "status = VALUES(status), "
                   + "priority = VALUES(priority), "
                   + "assigned_staff_id = VALUES(assigned_staff_id), "
                   + "remarks = VALUES(remarks)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, complaint.getComplaintId());
            ps.setString(2, complaint.getStudentId());
            ps.setString(3, complaint.getRoomNumber());
            ps.setString(4, complaint.getCategory());
            ps.setString(5, complaint.getDescription());
            ps.setString(6, complaint.getStatus().name());
            ps.setString(7, complaint.getPriority().name());
            ps.setString(8, assignedStaff);
            ps.setString(9, remarks);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error saving complaint " + complaint.getComplaintId() + ": " + e.getMessage());
            throw new RuntimeException("Database error saving complaint: " + e.getMessage(), e);
        }
    }

    /**
     * Inserts a new complaint and logs its initial PENDING state in complaint_history
     * within a single atomic database transaction.
     *
     * @param complaint Complaint to insert
     * @return true if inserted successfully
     * @throws SQLIntegrityConstraintViolationException if foreign key or primary key fails
     */
    public boolean insertComplaint(Complaint complaint) throws SQLIntegrityConstraintViolationException {
        if (complaint == null) return false;

        String assignedStaff = (complaint.getAssignedStaffId() != null && !complaint.getAssignedStaffId().trim().isEmpty())
                ? complaint.getAssignedStaffId().trim()
                : null;

        String remarks = (complaint.getRemarks() != null) ? complaint.getRemarks().trim() : "";

        String insertSql = "INSERT INTO complaints (complaint_id, student_id, room_number, category, description, status, priority, assigned_staff_id, remarks) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String historySql = "INSERT INTO complaint_history (complaint_id, old_status, new_status, updated_by) "
                          + "VALUES (?, NULL, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psInsert = conn.prepareStatement(insertSql);
                 PreparedStatement psHistory = conn.prepareStatement(historySql)) {

                psInsert.setString(1, complaint.getComplaintId());
                psInsert.setString(2, complaint.getStudentId());
                psInsert.setString(3, complaint.getRoomNumber());
                psInsert.setString(4, complaint.getCategory());
                psInsert.setString(5, complaint.getDescription());
                psInsert.setString(6, complaint.getStatus().name());
                psInsert.setString(7, complaint.getPriority().name());
                psInsert.setString(8, assignedStaff);
                psInsert.setString(9, remarks);

                int rows = psInsert.executeUpdate();

                // Log initial status history
                psHistory.setString(1, complaint.getComplaintId());
                psHistory.setString(2, complaint.getStatus().name());
                psHistory.setString(3, complaint.getStudentId());
                psHistory.executeUpdate();

                conn.commit();
                return rows > 0;

            } catch (SQLException ex) {
                conn.rollback();
                if (ex instanceof SQLIntegrityConstraintViolationException) {
                    throw (SQLIntegrityConstraintViolationException) ex;
                }
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLIntegrityConstraintViolationException e) {
            throw e;
        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error inserting complaint: " + e.getMessage());
            throw new RuntimeException("Database error inserting complaint: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<Complaint> loadComplaints() {
        ArrayList<Complaint> list = new ArrayList<>();
        String sql = "SELECT complaint_id, student_id, room_number, category, description, status, priority, assigned_staff_id, remarks "
                   + "FROM complaints ORDER BY created_at DESC, complaint_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToComplaint(rs));
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error loading complaints: " + e.getMessage());
        }

        return list;
    }

    @Override
    public Complaint findById(String complaintId) {
        if (complaintId == null || complaintId.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT complaint_id, student_id, room_number, category, description, status, priority, assigned_staff_id, remarks "
                   + "FROM complaints WHERE LOWER(complaint_id) = LOWER(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, complaintId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToComplaint(rs);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error finding complaint by ID: " + e.getMessage());
        }

        return null;
    }

    @Override
    public ArrayList<Complaint> findByStudentId(String studentId) {
        ArrayList<Complaint> list = new ArrayList<>();
        if (studentId == null || studentId.trim().isEmpty()) return list;

        String sql = "SELECT complaint_id, student_id, room_number, category, description, status, priority, assigned_staff_id, remarks "
                   + "FROM complaints WHERE LOWER(student_id) = LOWER(?) ORDER BY created_at DESC, complaint_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToComplaint(rs));
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error finding complaints for student: " + e.getMessage());
        }

        return list;
    }

    @Override
    public ArrayList<Complaint> findByAssignedStaffId(String staffId) {
        ArrayList<Complaint> list = new ArrayList<>();
        if (staffId == null || staffId.trim().isEmpty()) return list;

        String sql = "SELECT complaint_id, student_id, room_number, category, description, status, priority, assigned_staff_id, remarks "
                   + "FROM complaints WHERE LOWER(assigned_staff_id) = LOWER(?) ORDER BY created_at DESC, complaint_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, staffId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToComplaint(rs));
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error finding complaints for staff: " + e.getMessage());
        }

        return list;
    }

    @Override
    public boolean updateStatus(String complaintId, ComplaintStatus newStatus, String updatedBy) {
        if (complaintId == null || newStatus == null) return false;

        String selectSql = "SELECT status FROM complaints WHERE complaint_id = ? FOR UPDATE";
        String updateSql = "UPDATE complaints SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
        String historySql = "INSERT INTO complaint_history (complaint_id, old_status, new_status, updated_by) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            String oldStatus = null;
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setString(1, complaintId.trim());
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        oldStatus = rs.getString("status");
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql);
                 PreparedStatement psHistory = conn.prepareStatement(historySql)) {

                psUpdate.setString(1, newStatus.name());
                psUpdate.setString(2, complaintId.trim());
                int rows = psUpdate.executeUpdate();

                psHistory.setString(1, complaintId.trim());
                psHistory.setString(2, oldStatus);
                psHistory.setString(3, newStatus.name());
                psHistory.setString(4, (updatedBy != null && !updatedBy.trim().isEmpty()) ? updatedBy.trim() : null);
                psHistory.executeUpdate();

                conn.commit();
                return rows > 0;

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error updating status: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean assignStaff(String complaintId, String staffId) {
        return assignStaff(complaintId, staffId, null);
    }

    /**
     * Assigns staff to a complaint with the user who performed the assignment.
     */
    public boolean assignStaff(String complaintId, String staffId, String assignedBy) {
        if (complaintId == null || staffId == null || staffId.trim().isEmpty()) return false;

        String selectSql = "SELECT status FROM complaints WHERE complaint_id = ? FOR UPDATE";
        String updateSql = "UPDATE complaints SET assigned_staff_id = ?, status = 'ASSIGNED', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
        String historySql = "INSERT INTO complaint_history (complaint_id, old_status, new_status, updated_by) VALUES (?, ?, 'ASSIGNED', ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            String oldStatus = null;
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setString(1, complaintId.trim());
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        oldStatus = rs.getString("status");
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql);
                 PreparedStatement psHistory = conn.prepareStatement(historySql)) {

                psUpdate.setString(1, staffId.trim());
                psUpdate.setString(2, complaintId.trim());
                int rows = psUpdate.executeUpdate();

                psHistory.setString(1, complaintId.trim());
                psHistory.setString(2, oldStatus);
                psHistory.setString(3, (assignedBy != null && !assignedBy.trim().isEmpty()) ? assignedBy.trim() : null);
                psHistory.executeUpdate();

                conn.commit();
                return rows > 0;

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error assigning staff: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean addRemarks(String complaintId, String remarks) {
        if (complaintId == null) return false;

        String sql = "UPDATE complaints SET remarks = ?, updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, (remarks != null) ? remarks.trim() : "");
            ps.setString(2, complaintId.trim());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error adding remarks: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteComplaint(String complaintId) {
        if (complaintId == null) return false;

        String sql = "DELETE FROM complaints WHERE complaint_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, complaintId.trim());
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error deleting complaint: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getTotalCount() {
        String sql = "SELECT COUNT(*) FROM complaints";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error counting total complaints: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getCountByStatus(ComplaintStatus status) {
        if (status == null) return 0;
        String sql = "SELECT COUNT(*) FROM complaints WHERE status = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error counting complaints by status: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getCountByPriority(ComplaintPriority priority) {
        if (priority == null) return 0;
        String sql = "SELECT COUNT(*) FROM complaints WHERE priority = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, priority.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error counting complaints by priority: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public Map<String, Integer> getCategoryCounts() {
        Map<String, Integer> counts = new HashMap<>();
        String sql = "SELECT category, COUNT(*) as cnt FROM complaints GROUP BY category";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String cat = rs.getString("category");
                int count = rs.getInt("cnt");
                counts.put(cat != null && !cat.isEmpty() ? cat : "Other", count);
            }

        } catch (SQLException e) {
            System.err.println("[MySQLComplaintRepository] Error getting category counts: " + e.getMessage());
        }
        return counts;
    }

    /**
     * Helper to map a ResultSet row to a Complaint model instance.
     */
    private Complaint mapRowToComplaint(ResultSet rs) throws SQLException {
        String complaintId = rs.getString("complaint_id");
        String studentId = rs.getString("student_id");
        String roomNumber = rs.getString("room_number");
        String category = rs.getString("category");
        String description = rs.getString("description");
        String statusStr = rs.getString("status");
        String priorityStr = rs.getString("priority");
        String assignedStaffId = rs.getString("assigned_staff_id");
        String remarks = rs.getString("remarks");

        ComplaintPriority priority = ComplaintPriority.MEDIUM;
        try {
            if (priorityStr != null) {
                priority = ComplaintPriority.valueOf(priorityStr.toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {}

        ComplaintStatus status = ComplaintStatus.PENDING;
        try {
            if (statusStr != null) {
                status = ComplaintStatus.valueOf(statusStr.toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {}

        Complaint c = new Complaint(
                complaintId,
                studentId,
                roomNumber != null ? roomNumber : "",
                category != null ? category : "",
                description != null ? description : "",
                priority
        );

        c.setStatus(status);
        c.setAssignedStaffId(assignedStaffId != null ? assignedStaffId : "");
        c.setRemarks(remarks != null ? remarks : "");

        return c;
    }
}
