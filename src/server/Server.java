package server;

import exception.ComplaintNotFoundException;
import exception.InvalidCredentialsException;
import exception.InvalidInputException;
import exception.UserNotFoundException;
import model.*;
import service.AssignmentService;
import service.ComplaintService;
import service.ReportService;
import service.UserService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {

    private static final UserService userService = new UserService();
    private static final ComplaintService complaintService = new ComplaintService();
    private static final AssignmentService assignmentService = new AssignmentService();
    private static final ReportService reportService = new ReportService();

    public static void main(String[] args) throws IOException {

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/health", Server::healthCheck);
        server.createContext("/api/login", Server::login);
        server.createContext("/api/register", Server::register);
        server.createContext("/api/complaints", Server::handleComplaints);
        server.createContext("/api/reports", Server::handleReports);
        server.createContext("/api/users", Server::handleUsers);

        server.setExecutor(null);
        server.start();

        System.out.println("Hostel Complaint System Backend started at http://localhost:8080");
    }

    // -------------------------------------------------------------
    // Health Check Endpoint
    // -------------------------------------------------------------
    private static void healthCheck(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        String json = "{\"status\":\"UP\",\"message\":\"Hostel Complaint System Backend is running.\"}";
        sendJsonResponse(exchange, 200, json);
    }

    // -------------------------------------------------------------
    // Authentication: Login Endpoint
    // -------------------------------------------------------------
    private static void login(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        Map<String, String> data = readRequestBody(exchange);
        String username = data.get("username");
        String password = data.get("password");

        if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Username and password are required.\"}");
            return;
        }

        try {
            User user = userService.login(username.trim(), password);

            StringBuilder response = new StringBuilder("{");
            response.append("\"success\":true,");
            response.append("\"userId\":\"").append(escapeJson(user.getUserId())).append("\",");
            response.append("\"name\":\"").append(escapeJson(user.getName())).append("\",");
            response.append("\"username\":\"").append(escapeJson(user.getUsername())).append("\",");
            response.append("\"role\":\"").append(escapeJson(user.getRole())).append("\"");

            if (user instanceof Student) {
                Student student = (Student) user;
                response.append(",\"roomNumber\":\"").append(escapeJson(student.getRoomNumber())).append("\"");
            } else if (user instanceof Staff) {
                Staff staff = (Staff) user;
                response.append(",\"department\":\"").append(escapeJson(staff.getDepartment())).append("\"");
            }
            response.append("}");

            sendJsonResponse(exchange, 200, response.toString());

        } catch (UserNotFoundException e) {
            sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (InvalidCredentialsException e) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred.\"}");
        }
    }

    // -------------------------------------------------------------
    // Authentication: Student Registration Endpoint
    // -------------------------------------------------------------
    private static void register(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        Map<String, String> data = readRequestBody(exchange);
        String studentId = data.get("studentId");
        String roomNumber = data.get("roomNumber");
        String name = data.get("name");
        String username = data.get("username");
        String password = data.get("password");

        if (studentId == null || studentId.trim().isEmpty() ||
                roomNumber == null || roomNumber.trim().isEmpty() ||
                name == null || name.trim().isEmpty() ||
                username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {

            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"All registration fields are required.\"}");
            return;
        }

        Student student = new Student(
                studentId.trim(),
                name.trim(),
                username.trim(),
                password,
                roomNumber.trim()
        );

        try {
            userService.registerUser(student);

            String response = "{"
                    + "\"success\":true,"
                    + "\"message\":\"Registration successful.\","
                    + "\"userId\":\"" + escapeJson(student.getUserId()) + "\","
                    + "\"name\":\"" + escapeJson(student.getName()) + "\","
                    + "\"username\":\"" + escapeJson(student.getUsername()) + "\","
                    + "\"role\":\"STUDENT\","
                    + "\"roomNumber\":\"" + escapeJson(student.getRoomNumber()) + "\""
                    + "}";

            sendJsonResponse(exchange, 201, response);

        } catch (InvalidInputException e) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"An error occurred while registering user.\"}");
        }
    }

    // -------------------------------------------------------------
    // Complaints Handler (Routes /api/complaints and sub-paths)
    // -------------------------------------------------------------
    private static void handleComplaints(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        // Check authentication
        User caller = authenticateCaller(exchange);
        if (caller == null) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Authentication required. Please sign in.\"}");
            return;
        }

        // Parse path segments: /api/complaints[/{id}[/{action}]]
        String[] parts = path.split("/");

        try {
            // Case 1: Exact /api/complaints or /api/complaints/
            if (parts.length == 3 || (parts.length == 4 && parts[3].isEmpty())) {
                if (method.equals("GET")) {
                    handleGetAllComplaints(exchange, caller);
                } else if (method.equals("POST")) {
                    handleCreateComplaint(exchange, caller);
                } else {
                    sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                }
                return;
            }

            // Case 2: Sub-resource paths
            if (parts.length == 5 && parts[3].equalsIgnoreCase("student")) {
                // GET /api/complaints/student/{studentId}
                if (!method.equals("GET")) {
                    sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                    return;
                }
                String studentId = URLDecoder.decode(parts[4], StandardCharsets.UTF_8).trim();
                handleGetStudentComplaints(exchange, caller, studentId);
                return;
            }

            if (parts.length == 5 && parts[3].equalsIgnoreCase("staff")) {
                // GET /api/complaints/staff/{staffId}
                if (!method.equals("GET")) {
                    sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                    return;
                }
                String staffId = URLDecoder.decode(parts[4], StandardCharsets.UTF_8).trim();
                handleGetStaffComplaints(exchange, caller, staffId);
                return;
            }

            if (parts.length == 5 && parts[4].equalsIgnoreCase("status")) {
                // PUT /api/complaints/{complaintId}/status
                if (!method.equals("PUT")) {
                    sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                    return;
                }
                String complaintId = URLDecoder.decode(parts[3], StandardCharsets.UTF_8).trim();
                handleUpdateStatus(exchange, caller, complaintId);
                return;
            }

            if (parts.length == 5 && parts[4].equalsIgnoreCase("remarks")) {
                // PUT /api/complaints/{complaintId}/remarks
                if (!method.equals("PUT")) {
                    sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                    return;
                }
                String complaintId = URLDecoder.decode(parts[3], StandardCharsets.UTF_8).trim();
                handleAddRemarks(exchange, caller, complaintId);
                return;
            }

            if (parts.length == 5 && parts[4].equalsIgnoreCase("assign")) {
                // PUT /api/complaints/{complaintId}/assign
                if (!method.equals("PUT")) {
                    sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                    return;
                }
                String complaintId = URLDecoder.decode(parts[3], StandardCharsets.UTF_8).trim();
                handleAssignComplaint(exchange, caller, complaintId);
                return;
            }

            if (parts.length == 4) {
                // GET /api/complaints/{complaintId}
                if (!method.equals("GET")) {
                    sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                    return;
                }
                String complaintId = URLDecoder.decode(parts[3], StandardCharsets.UTF_8).trim();
                handleGetComplaintDetails(exchange, caller, complaintId);
                return;
            }

            sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Endpoint not found.\"}");

        } catch (Exception e) {
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"An error occurred: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleGetAllComplaints(HttpExchange exchange, User caller) throws IOException {
        // Admins can see all complaints. Students see their own. Staff see assigned.
        ArrayList<Complaint> list;
        if ("ADMIN".equalsIgnoreCase(caller.getRole())) {
            list = complaintService.getAllComplaints();
        } else if ("STUDENT".equalsIgnoreCase(caller.getRole())) {
            list = complaintService.getComplaintsByStudent(caller.getUserId());
        } else if ("STAFF".equalsIgnoreCase(caller.getRole())) {
            list = assignmentService.getComplaintsByStaff(caller.getUserId());
        } else {
            sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Forbidden.\"}");
            return;
        }

        sendJsonResponse(exchange, 200, complaintsToJson(list));
    }

    private static void handleCreateComplaint(HttpExchange exchange, User caller) throws IOException {
        if (!"STUDENT".equalsIgnoreCase(caller.getRole()) && !"ADMIN".equalsIgnoreCase(caller.getRole())) {
            sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Only students or administrators can lodge complaints.\"}");
            return;
        }

        Map<String, String> data = readRequestBody(exchange);
        String studentId = data.get("studentId");
        String roomNumber = data.get("roomNumber");
        String category = data.get("category");
        String description = data.get("description");
        String priorityStr = data.get("priority");
        String complaintId = data.get("complaintId");

        // If caller is student, studentId must match caller ID
        if ("STUDENT".equalsIgnoreCase(caller.getRole())) {
            if (studentId == null || studentId.trim().isEmpty()) {
                studentId = caller.getUserId();
            } else if (!studentId.trim().equalsIgnoreCase(caller.getUserId())) {
                sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"You cannot submit a complaint on behalf of another student.\"}");
                return;
            }
        }

        if (studentId == null || studentId.trim().isEmpty() ||
                roomNumber == null || roomNumber.trim().isEmpty() ||
                category == null || category.trim().isEmpty() ||
                description == null || description.trim().isEmpty() ||
                priorityStr == null || priorityStr.trim().isEmpty()) {

            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"All complaint fields (room, category, description, priority) are required.\"}");
            return;
        }

        ComplaintPriority priority;
        try {
            priority = ComplaintPriority.valueOf(priorityStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid priority value. Choose LOW, MEDIUM, or HIGH.\"}");
            return;
        }

        if (complaintId == null || complaintId.trim().isEmpty()) {
            complaintId = generateNextComplaintId();
        } else {
            complaintId = complaintId.trim().toUpperCase();
        }

        Complaint complaint = new Complaint(
                complaintId,
                studentId.trim(),
                roomNumber.trim(),
                category.trim(),
                description.trim(),
                priority
        );

        try {
            complaintService.createComplaint(complaint);
            String resp = "{\"success\":true,\"message\":\"Complaint created successfully.\",\"complaint\":" + complaintToJson(complaint) + "}";
            sendJsonResponse(exchange, 201, resp);
        } catch (InvalidInputException e) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleGetStudentComplaints(HttpExchange exchange, User caller, String studentId) throws IOException {
        if ("STUDENT".equalsIgnoreCase(caller.getRole()) && !caller.getUserId().equalsIgnoreCase(studentId)) {
            sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. You cannot view another student's complaints.\"}");
            return;
        }

        ArrayList<Complaint> list = complaintService.getComplaintsByStudent(studentId);
        sendJsonResponse(exchange, 200, complaintsToJson(list));
    }

    private static void handleGetStaffComplaints(HttpExchange exchange, User caller, String staffId) throws IOException {
        if ("STAFF".equalsIgnoreCase(caller.getRole()) && !caller.getUserId().equalsIgnoreCase(staffId)) {
            sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. You cannot view another staff member's complaints.\"}");
            return;
        }

        ArrayList<Complaint> list = assignmentService.getComplaintsByStaff(staffId);
        sendJsonResponse(exchange, 200, complaintsToJson(list));
    }

    private static void handleGetComplaintDetails(HttpExchange exchange, User caller, String complaintId) throws IOException {
        try {
            Complaint complaint = complaintService.getComplaintById(complaintId);

            if ("STUDENT".equalsIgnoreCase(caller.getRole()) && !caller.getUserId().equalsIgnoreCase(complaint.getStudentId())) {
                sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. You do not own this complaint.\"}");
                return;
            }

            sendJsonResponse(exchange, 200, complaintToJson(complaint));

        } catch (ComplaintNotFoundException e) {
            sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleUpdateStatus(HttpExchange exchange, User caller, String complaintId) throws IOException {
        Map<String, String> data = readRequestBody(exchange);
        String statusStr = data.get("status");

        if (statusStr == null || statusStr.trim().isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Status is required.\"}");
            return;
        }

        ComplaintStatus newStatus;
        try {
            newStatus = ComplaintStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid complaint status value.\"}");
            return;
        }

        try {
            Complaint complaint = complaintService.getComplaintById(complaintId);

            if ("STAFF".equalsIgnoreCase(caller.getRole())) {
                if (complaint.getAssignedStaffId() == null || !complaint.getAssignedStaffId().equalsIgnoreCase(caller.getUserId())) {
                    sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. You can only update complaints assigned to you.\"}");
                    return;
                }
            } else if (!"ADMIN".equalsIgnoreCase(caller.getRole())) {
                sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. Only assigned staff or admin can update status.\"}");
                return;
            }

            complaintService.updateStatus(complaintId, newStatus);
            Complaint updated = complaintService.getComplaintById(complaintId);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Complaint status updated successfully.\",\"complaint\":" + complaintToJson(updated) + "}");

        } catch (ComplaintNotFoundException e) {
            sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (InvalidInputException e) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleAddRemarks(HttpExchange exchange, User caller, String complaintId) throws IOException {
        Map<String, String> data = readRequestBody(exchange);
        String remarks = data.get("remarks");

        if (remarks == null || remarks.trim().isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Remarks cannot be empty.\"}");
            return;
        }

        try {
            Complaint complaint = complaintService.getComplaintById(complaintId);

            if ("STAFF".equalsIgnoreCase(caller.getRole())) {
                if (complaint.getAssignedStaffId() == null || !complaint.getAssignedStaffId().equalsIgnoreCase(caller.getUserId())) {
                    sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. You can only add remarks to complaints assigned to you.\"}");
                    return;
                }
            } else if (!"ADMIN".equalsIgnoreCase(caller.getRole())) {
                sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. Only assigned staff or admin can add remarks.\"}");
                return;
            }

            complaintService.addRemarks(complaintId, remarks.trim());
            Complaint updated = complaintService.getComplaintById(complaintId);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Remarks added successfully.\",\"complaint\":" + complaintToJson(updated) + "}");

        } catch (ComplaintNotFoundException e) {
            sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (InvalidInputException e) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleAssignComplaint(HttpExchange exchange, User caller, String complaintId) throws IOException {
        if (!"ADMIN".equalsIgnoreCase(caller.getRole())) {
            sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. Only administrators can assign complaints.\"}");
            return;
        }

        Map<String, String> data = readRequestBody(exchange);
        String staffId = data.get("staffId");

        if (staffId == null || staffId.trim().isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Staff ID cannot be empty.\"}");
            return;
        }

        User staffUser = userService.findUserById(staffId.trim());
        if (staffUser == null || !"STAFF".equalsIgnoreCase(staffUser.getRole())) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Assigned user must be a valid staff member.\"}");
            return;
        }

        try {
            assignmentService.assignComplaint(complaintId, staffId.trim());
            Complaint updated = complaintService.getComplaintById(complaintId);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Complaint assigned successfully.\",\"complaint\":" + complaintToJson(updated) + "}");

        } catch (ComplaintNotFoundException e) {
            sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (InvalidInputException e) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // -------------------------------------------------------------
    // Reports Endpoint
    // -------------------------------------------------------------
    private static void handleReports(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        User caller = authenticateCaller(exchange);
        if (caller == null) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Authentication required.\"}");
            return;
        }

        if (!"ADMIN".equalsIgnoreCase(caller.getRole())) {
            sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. Only administrators can view reports.\"}");
            return;
        }

        int total = reportService.getTotalComplaints();
        int pending = reportService.getComplaintsByStatus(ComplaintStatus.PENDING);
        int assigned = reportService.getComplaintsByStatus(ComplaintStatus.ASSIGNED);
        int inProgress = reportService.getComplaintsByStatus(ComplaintStatus.IN_PROGRESS);
        int resolved = reportService.getComplaintsByStatus(ComplaintStatus.RESOLVED);

        Map<String, Integer> categories = reportService.getCategoryCounts();
        Map<String, Integer> priorities = new HashMap<>();
        priorities.put("LOW", reportService.getComplaintsByPriority(ComplaintPriority.LOW));
        priorities.put("MEDIUM", reportService.getComplaintsByPriority(ComplaintPriority.MEDIUM));
        priorities.put("HIGH", reportService.getComplaintsByPriority(ComplaintPriority.HIGH));

        // Background thread demonstration as per project requirements
        reportService.generateReportInBackground();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":true,");
        sb.append("\"total\":").append(total).append(",");
        sb.append("\"pending\":").append(pending).append(",");
        sb.append("\"assigned\":").append(assigned).append(",");
        sb.append("\"inProgress\":").append(inProgress).append(",");
        sb.append("\"resolved\":").append(resolved).append(",");

        sb.append("\"categories\":{");
        int ci = 0;
        for (Map.Entry<String, Integer> entry : categories.entrySet()) {
            if (ci > 0) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            ci++;
        }
        sb.append("},");

        sb.append("\"priorities\":{");
        int pi = 0;
        for (Map.Entry<String, Integer> entry : priorities.entrySet()) {
            if (pi > 0) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            pi++;
        }
        sb.append("}");

        sb.append("}");

        sendJsonResponse(exchange, 200, sb.toString());
    }

    // -------------------------------------------------------------
    // Users Management Endpoint (/api/users/students, /api/users/staff)
    // -------------------------------------------------------------
    private static void handleUsers(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        User caller = authenticateCaller(exchange);
        if (caller == null) {
            sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Authentication required.\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if (path.endsWith("/students")) {
            if (!"ADMIN".equalsIgnoreCase(caller.getRole())) {
                sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied. Admin role required.\"}");
                return;
            }
            ArrayList<Student> students = userService.getStudents();
            sendJsonResponse(exchange, 200, usersToJson(students));
            return;
        }

        if (path.endsWith("/staff")) {
            // Both ADMIN and authorized users can read staff list for display/assignment
            ArrayList<Staff> staffList = userService.getStaffList();
            sendJsonResponse(exchange, 200, usersToJson(staffList));
            return;
        }

        sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Endpoint not found.\"}");
    }

    // -------------------------------------------------------------
    // Security & Authorization Helper
    // -------------------------------------------------------------
    private static User authenticateCaller(HttpExchange exchange) {
        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");

        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        User user = userService.findUserById(userId.trim());
        if (user == null) {
            return null;
        }

        if (userRole != null && !userRole.trim().isEmpty()) {
            if (!userRole.trim().equalsIgnoreCase(user.getRole())) {
                return null;
            }
        }

        return user;
    }

    // -------------------------------------------------------------
    // ID Generator Helper
    // -------------------------------------------------------------
    private static synchronized String generateNextComplaintId() {
        int max = 0;
        for (Complaint c : complaintService.getAllComplaints()) {
            String id = c.getComplaintId();
            if (id != null && id.toUpperCase().startsWith("C")) {
                try {
                    int num = Integer.parseInt(id.substring(1));
                    if (num > max) max = num;
                } catch (Exception ignored) {}
            }
        }
        return String.format("C%03d", max + 1);
    }

    // -------------------------------------------------------------
    // Request & JSON Helpers
    // -------------------------------------------------------------
    private static boolean handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-User-Id, X-User-Role, Authorization");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static Map<String, String> readRequestBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

        if (body.isEmpty()) {
            return new HashMap<>();
        }

        if (body.startsWith("{") && body.endsWith("}")) {
            return parseJsonObject(body);
        }

        return parseFormData(body);
    }

    private static Map<String, String> parseFormData(String body) {
        Map<String, String> data = new HashMap<>();
        if (body == null || body.isEmpty()) return data;

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                data.put(key, value);
            } else if (keyValue.length == 1) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                data.put(key, "");
            }
        }
        return data;
    }

    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return map;

        int i = 1;
        int n = json.length() - 1;

        while (i < n) {
            while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) {
                i++;
            }
            if (i >= n) break;

            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            i++; // skip open quote
            StringBuilder keySb = new StringBuilder();
            while (i < n && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < n) {
                    i++;
                    char c = json.charAt(i);
                    if (c == '"' || c == '\\' || c == '/') {
                        keySb.append(c);
                    } else if (c == 'n') {
                        keySb.append('\n');
                    } else if (c == 'r') {
                        keySb.append('\r');
                    } else if (c == 't') {
                        keySb.append('\t');
                    } else if (c == 'u' && i + 4 < n) {
                        String hex = json.substring(i + 1, i + 5);
                        try {
                            keySb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException nfe) {
                            keySb.append('u');
                        }
                    } else {
                        keySb.append(c);
                    }
                } else {
                    keySb.append(json.charAt(i));
                }
                i++;
            }
            i++; // skip close quote

            while (i < n && json.charAt(i) != ':') {
                i++;
            }
            i++; // skip colon

            while (i < n && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= n) break;

            String val = "";
            if (json.charAt(i) == '"') {
                i++; // skip open quote
                StringBuilder valSb = new StringBuilder();
                while (i < n && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\' && i + 1 < n) {
                        i++;
                        char c = json.charAt(i);
                        if (c == '"' || c == '\\' || c == '/') {
                            valSb.append(c);
                        } else if (c == 'n') {
                            valSb.append('\n');
                        } else if (c == 'r') {
                            valSb.append('\r');
                        } else if (c == 't') {
                            valSb.append('\t');
                        } else if (c == 'u' && i + 4 < n) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                valSb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException nfe) {
                                valSb.append('u');
                            }
                        } else {
                            valSb.append(c);
                        }
                    } else {
                        valSb.append(json.charAt(i));
                    }
                    i++;
                }
                i++; // skip close quote
                val = valSb.toString();
            } else {
                StringBuilder valSb = new StringBuilder();
                while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}' && !Character.isWhitespace(json.charAt(i))) {
                    valSb.append(json.charAt(i));
                    i++;
                }
                val = valSb.toString();
                if ("null".equalsIgnoreCase(val)) {
                    val = null;
                }
            }
            map.put(keySb.toString(), val);
        }
        return map;
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    private static String complaintToJson(Complaint c) {
        if (c == null) return "null";
        return "{"
                + "\"complaintId\":\"" + escapeJson(c.getComplaintId()) + "\","
                + "\"studentId\":\"" + escapeJson(c.getStudentId()) + "\","
                + "\"roomNumber\":\"" + escapeJson(c.getRoomNumber()) + "\","
                + "\"category\":\"" + escapeJson(c.getCategory()) + "\","
                + "\"description\":\"" + escapeJson(c.getDescription()) + "\","
                + "\"status\":\"" + c.getStatus().name() + "\","
                + "\"priority\":\"" + c.getPriority().name() + "\","
                + "\"assignedStaffId\":\"" + escapeJson(c.getAssignedStaffId()) + "\","
                + "\"remarks\":\"" + escapeJson(c.getRemarks()) + "\""
                + "}";
    }

    private static String complaintsToJson(List<Complaint> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(complaintToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String userToJson(User u) {
        if (u == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"userId\":\"").append(escapeJson(u.getUserId())).append("\",");
        sb.append("\"name\":\"").append(escapeJson(u.getName())).append("\",");
        sb.append("\"username\":\"").append(escapeJson(u.getUsername())).append("\",");
        sb.append("\"role\":\"").append(escapeJson(u.getRole())).append("\"");

        if (u instanceof Student) {
            sb.append(",\"roomNumber\":\"").append(escapeJson(((Student) u).getRoomNumber())).append("\"");
        } else if (u instanceof Staff) {
            sb.append(",\"department\":\"").append(escapeJson(((Staff) u).getDepartment())).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String usersToJson(List<? extends User> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(userToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-User-Id, X-User-Role, Authorization");

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(responseBytes);
        }
    }
}