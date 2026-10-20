package model;

public class Complaint {

    private String complaintId;
    private String studentId;
    private String roomNumber;
    private String category;
    private String description;

    private ComplaintStatus status;
    private ComplaintPriority priority;

    private String assignedStaffId;
    private String remarks;

    public Complaint(String complaintId,
                     String studentId,
                     String roomNumber,
                     String category,
                     String description,
                     ComplaintPriority priority) {

        this.complaintId = complaintId;
        this.studentId = studentId;
        this.roomNumber = roomNumber;
        this.category = category;
        this.description = description;
        this.priority = priority;

        this.status = ComplaintStatus.PENDING;
        this.assignedStaffId = "";
        this.remarks = "";
    }

    public String getComplaintId() {
        return complaintId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public ComplaintPriority getPriority() {
        return priority;
    }

    public String getAssignedStaffId() {
        return assignedStaffId;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public void setPriority(ComplaintPriority priority) {
        this.priority = priority;
    }

    public void setAssignedStaffId(String assignedStaffId) {
        this.assignedStaffId = assignedStaffId;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}