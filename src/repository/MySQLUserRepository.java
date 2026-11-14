package repository;

import database.DatabaseConnection;
import model.Admin;
import model.Staff;
import model.Student;
import model.User;
import util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;

/**
 * MySQLUserRepository
 *
 * Database-backed repository for User entities (Student, Staff, Admin).
 * Implements the UserRepository interface, ensuring seamless compatibility
 * while executing secure JDBC queries via PreparedStatement.
 * Automatically hashes passwords using BCrypt on insert/update.
 */
public class MySQLUserRepository implements UserRepository {

    @Override
    public void saveUser(User user) {
        if (user == null) {
            return;
        }

        String role = user.getRole();
        String roomNumber = null;
        String department = null;

        if (user instanceof Student) {
            roomNumber = ((Student) user).getRoomNumber();
        } else if (user instanceof Staff) {
            department = ((Staff) user).getDepartment();
        }

        // Hash plaintext password before persisting if not already hashed
        String hashedPassword = PasswordUtil.hashPassword(user.getPassword());

        String sql = "INSERT INTO users (user_id, name, username, password, role, room_number, department, status, phone) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE "
                   + "name = VALUES(name), "
                   + "password = VALUES(password), "
                   + "role = VALUES(role), "
                   + "room_number = VALUES(room_number), "
                   + "department = VALUES(department), "
                   + "status = VALUES(status), "
                   + "phone = VALUES(phone)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUserId());
            ps.setString(2, user.getName());
            ps.setString(3, user.getUsername());
            ps.setString(4, hashedPassword);
            ps.setString(5, role);
            ps.setString(6, roomNumber);
            ps.setString(7, department);
            ps.setString(8, user.getStatus());
            ps.setString(9, user.getPhone());

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error saving user " + user.getUserId() + ": " + e.getMessage());
            throw new RuntimeException("Database error saving user: " + e.getMessage(), e);
        }
    }

    /**
     * Inserts a new user specifically without upsert, throwing an exception on duplicate keys.
     *
     * @param user User to insert
     * @return true if inserted, false on failure
     * @throws SQLIntegrityConstraintViolationException if duplicate key exists
     */
    public boolean insertUser(User user) throws SQLIntegrityConstraintViolationException {
        if (user == null) return false;

        String role = user.getRole();
        String roomNumber = null;
        String department = null;

        if (user instanceof Student) {
            roomNumber = ((Student) user).getRoomNumber();
        } else if (user instanceof Staff) {
            department = ((Staff) user).getDepartment();
        }

        String hashedPassword = PasswordUtil.hashPassword(user.getPassword());

        String sql = "INSERT INTO users (user_id, name, username, password, role, room_number, department, status, phone) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUserId());
            ps.setString(2, user.getName());
            ps.setString(3, user.getUsername());
            ps.setString(4, hashedPassword);
            ps.setString(5, role);
            ps.setString(6, roomNumber);
            ps.setString(7, department);
            ps.setString(8, user.getStatus());
            ps.setString(9, user.getPhone());

            return ps.executeUpdate() > 0;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw e; // Re-throw to allow caller to handle duplicate username / user_id
        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error inserting user: " + e.getMessage());
            throw new RuntimeException("Database error inserting user: " + e.getMessage(), e);
        }
    }

    @Override
    public ArrayList<User> loadUsers() {
        ArrayList<User> users = new ArrayList<>();
        String sql = "SELECT user_id, name, username, password, role, room_number, department, status, phone FROM users ORDER BY user_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = mapRowToUser(rs);
                if (u != null) {
                    users.add(u);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error loading users: " + e.getMessage());
        }

        return users;
    }

    /**
     * Finds a user by their unique user_id.
     */
    public User findById(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT user_id, name, username, password, role, room_number, department, status, phone "
                   + "FROM users WHERE LOWER(user_id) = LOWER(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error finding user by ID: " + e.getMessage());
        }

        return null;
    }

    /**
     * Finds a user by their unique username.
     */
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT user_id, name, username, password, role, room_number, department, status, phone "
                   + "FROM users WHERE LOWER(username) = LOWER(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error finding user by username: " + e.getMessage());
        }

        return null;
    }

    /**
     * Checks if a username already exists in the database.
     */
    public boolean existsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error checking username existence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a user ID already exists in the database.
     */
    public boolean existsById(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT 1 FROM users WHERE LOWER(user_id) = LOWER(?) LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error checking ID existence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns all Student records from the database.
     */
    public ArrayList<Student> getStudents() {
        ArrayList<Student> students = new ArrayList<>();
        String sql = "SELECT user_id, name, username, password, role, room_number, department, status, phone "
                   + "FROM users WHERE role = 'STUDENT' ORDER BY user_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = mapRowToUser(rs);
                if (u instanceof Student) {
                    students.add((Student) u);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error loading students: " + e.getMessage());
        }

        return students;
    }

    /**
     * Returns all Staff records from the database.
     */
    public ArrayList<Staff> getStaffList() {
        ArrayList<Staff> staffList = new ArrayList<>();
        String sql = "SELECT user_id, name, username, password, role, room_number, department, status, phone "
                   + "FROM users WHERE role = 'STAFF' ORDER BY user_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = mapRowToUser(rs);
                if (u instanceof Staff) {
                    staffList.add((Staff) u);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error loading staff: " + e.getMessage());
        }

        return staffList;
    }

    /**
     * Returns all active Staff records from the database.
     */
    public ArrayList<Staff> getActiveStaffList() {
        ArrayList<Staff> staffList = new ArrayList<>();
        String sql = "SELECT user_id, name, username, password, role, room_number, department, status, phone "
                   + "FROM users WHERE role = 'STAFF' AND UPPER(status) = 'ACTIVE' ORDER BY user_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = mapRowToUser(rs);
                if (u instanceof Staff) {
                    staffList.add((Staff) u);
                }
            }

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error loading active staff: " + e.getMessage());
        }

        return staffList;
    }

    /**
     * Updates an existing staff member's profile details (name, department, phone).
     */
    public boolean updateStaffProfile(String userId, String name, String department, String phone) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        String sql = "UPDATE users SET name = ?, department = ?, phone = ? WHERE user_id = ? AND role = 'STAFF'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name != null ? name.trim() : "");
            ps.setString(2, department != null ? department.trim() : "");
            ps.setString(3, phone != null ? phone.trim() : "");
            ps.setString(4, userId.trim());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error updating staff profile: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates an existing staff member's status (ACTIVE / INACTIVE).
     */
    public boolean updateStaffStatus(String userId, String status) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        String normalized = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : "ACTIVE";
        String sql = "UPDATE users SET status = ? WHERE user_id = ? AND role = 'STAFF'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, normalized);
            ps.setString(2, userId.trim());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error updating staff status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates an existing user's password (e.g., during migration or reset).
     */
    public boolean updatePassword(String userId, String plainOrHashedPassword) {
        String hashed = PasswordUtil.hashPassword(plainOrHashedPassword);
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, hashed);
            ps.setString(2, userId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error updating password: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a user by ID.
     */
    public boolean deleteUser(String userId) {
        String sql = "DELETE FROM users WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[MySQLUserRepository] Error deleting user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Maps a ResultSet row to the appropriate User subclass.
     */
    private User mapRowToUser(ResultSet rs) throws SQLException {
        String userId = rs.getString("user_id");
        String name = rs.getString("name");
        String username = rs.getString("username");
        String password = rs.getString("password");
        String role = rs.getString("role");
        String roomNumber = rs.getString("room_number");
        String department = rs.getString("department");
        String status = rs.getString("status");
        String phone = rs.getString("phone");

        if (role == null) {
            role = "STUDENT";
        }
        if (status == null || status.trim().isEmpty()) {
            status = "ACTIVE";
        }
        if (phone == null) {
            phone = "";
        }

        switch (role.toUpperCase()) {
            case "STUDENT":
                return new Student(userId, name, username, password, roomNumber != null ? roomNumber : "", status, phone);
            case "STAFF":
                return new Staff(userId, name, username, password, department != null ? department : "", status, phone);
            case "ADMIN":
                return new Admin(userId, name, username, password, status, phone);
            default:
                return new Student(userId, name, username, password, roomNumber != null ? roomNumber : "", status, phone);
        }
    }
}
