package service;

import exception.InvalidCredentialsException;
import exception.InvalidInputException;
import exception.UserNotFoundException;
import model.Staff;
import model.Student;
import model.User;
import repository.MySQLUserRepository;
import repository.UserRepository;
import util.PasswordUtil;

import java.util.ArrayList;

/**
 * UserService
 *
 * Core business service managing user accounts, authentication, and role retrieval.
 * Uses MySQL as its primary persistence layer through MySQLUserRepository,
 * with BCrypt password hashing and transparent plaintext migration support.
 */
public class UserService {

    private final UserRepository userRepository;
    private final MySQLUserRepository mySQLUserRepository;

    public UserService() {
        this.mySQLUserRepository = new MySQLUserRepository();
        this.userRepository = this.mySQLUserRepository;
    }

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        if (userRepository instanceof MySQLUserRepository) {
            this.mySQLUserRepository = (MySQLUserRepository) userRepository;
        } else {
            this.mySQLUserRepository = null;
        }
    }

    /**
     * Registers a new user with comprehensive validation.
     *
     * @param user User entity to register
     * @throws InvalidInputException if validation fails or user already exists
     */
    public void registerUser(User user) throws InvalidInputException {
        if (user == null) {
            throw new InvalidInputException("User cannot be null.");
        }

        if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
            throw new InvalidInputException("User ID cannot be empty.");
        }

        if (findUserById(user.getUserId().trim()) != null) {
            throw new InvalidInputException("User ID already exists.");
        }

        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new InvalidInputException("Name cannot be empty.");
        }

        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new InvalidInputException("Username cannot be empty.");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new InvalidInputException("Password cannot be empty.");
        }

        if (findUserByUsername(user.getUsername().trim()) != null) {
            throw new InvalidInputException("Username already exists.");
        }

        userRepository.saveUser(user);

        System.out.println("User " + user.getUsername() + " registered successfully.");
    }

    /**
     * Finds a user by their user_id.
     */
    public User findUserById(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        if (mySQLUserRepository != null) {
            return mySQLUserRepository.findById(userId.trim());
        }

        // Fallback for non-MySQL implementations
        ArrayList<User> users = userRepository.loadUsers();
        for (User user : users) {
            if (user.getUserId().equalsIgnoreCase(userId.trim())) {
                return user;
            }
        }
        return null;
    }

    /**
     * Finds a user by their unique username.
     */
    public User findUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        if (mySQLUserRepository != null) {
            return mySQLUserRepository.findByUsername(username.trim());
        }

        // Fallback for non-MySQL implementations
        ArrayList<User> users = userRepository.loadUsers();
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username.trim())) {
                return user;
            }
        }
        return null;
    }

    /**
     * Retrieves all users.
     */
    public ArrayList<User> getAllUsers() {
        return userRepository.loadUsers();
    }

    /**
     * Retrieves all registered students.
     */
    public ArrayList<Student> getStudents() {
        if (mySQLUserRepository != null) {
            return mySQLUserRepository.getStudents();
        }

        ArrayList<Student> list = new ArrayList<>();
        for (User u : userRepository.loadUsers()) {
            if (u instanceof Student) {
                list.add((Student) u);
            }
        }
        return list;
    }

    /**
     * Retrieves all registered staff members.
     */
    public ArrayList<Staff> getStaffList() {
        if (mySQLUserRepository != null) {
            return mySQLUserRepository.getStaffList();
        }

        ArrayList<Staff> list = new ArrayList<>();
        for (User u : userRepository.loadUsers()) {
            if (u instanceof Staff) {
                list.add((Staff) u);
            }
        }
        return list;
    }

    /**
     * Retrieves all active registered staff members.
     */
    public ArrayList<Staff> getActiveStaffList() {
        if (mySQLUserRepository != null) {
            return mySQLUserRepository.getActiveStaffList();
        }

        ArrayList<Staff> list = new ArrayList<>();
        for (User u : userRepository.loadUsers()) {
            if (u instanceof Staff && u.isActive()) {
                list.add((Staff) u);
            }
        }
        return list;
    }

    /**
     * Generates the next sequential Staff ID (e.g. ST001, ST002, ... ST004).
     */
    public synchronized String generateNextStaffId() {
        int max = 0;
        ArrayList<Staff> staffList = getStaffList();
        for (Staff s : staffList) {
            String id = s.getUserId();
            if (id != null && id.toUpperCase().startsWith("ST")) {
                try {
                    int num = Integer.parseInt(id.substring(2));
                    if (num > max) max = num;
                } catch (NumberFormatException ignored) {}
            }
        }

        int nextNum = max + 1;
        String candidate = String.format("ST%03d", nextNum);
        while (findUserById(candidate) != null) {
            nextNum++;
            candidate = String.format("ST%03d", nextNum);
        }
        return candidate;
    }

    /**
     * Creates and registers a new Staff member with validation and BCrypt password hashing.
     *
     * @param name Full name
     * @param username Unique username
     * @param password Plaintext password to hash
     * @param department Assigned department
     * @param phone Contact number
     * @return Newly created Staff object
     * @throws InvalidInputException if input validation fails or username exists
     */
    public Staff createStaff(String name, String username, String password, String department, String phone)
            throws InvalidInputException {

        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("Full name cannot be empty.");
        }

        if (username == null || username.trim().isEmpty()) {
            throw new InvalidInputException("Username cannot be empty.");
        }

        if (username.trim().length() < 3) {
            throw new InvalidInputException("Username must be at least 3 characters.");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new InvalidInputException("Password cannot be empty.");
        }

        if (password.trim().length() < 4) {
            throw new InvalidInputException("Password must be at least 4 characters long.");
        }

        if (department == null || department.trim().isEmpty()) {
            throw new InvalidInputException("Department must be selected.");
        }

        if (findUserByUsername(username.trim()) != null) {
            throw new InvalidInputException("Username already exists.");
        }

        String staffId = generateNextStaffId();
        String hashedPassword = PasswordUtil.hashPassword(password.trim());
        String cleanPhone = phone != null ? phone.trim() : "";

        Staff staff = new Staff(
                staffId,
                name.trim(),
                username.trim(),
                hashedPassword,
                department.trim(),
                "ACTIVE",
                cleanPhone
        );

        userRepository.saveUser(staff);
        System.out.println("[UserService] Staff created successfully: " + staffId + " (" + staff.getUsername() + ")");
        return staff;
    }

    /**
     * Updates an existing staff member's profile details.
     *
     * @param staffId Staff User ID
     * @param name Updated full name
     * @param department Updated department
     * @param phone Updated contact phone
     * @return true if updated successfully
     * @throws UserNotFoundException if staff member is not found
     * @throws InvalidInputException if parameters are invalid
     */
    public boolean updateStaff(String staffId, String name, String department, String phone)
            throws UserNotFoundException, InvalidInputException {

        if (staffId == null || staffId.trim().isEmpty()) {
            throw new InvalidInputException("Staff ID cannot be empty.");
        }

        User user = findUserById(staffId.trim());
        if (user == null || !"STAFF".equalsIgnoreCase(user.getRole())) {
            throw new UserNotFoundException("Staff member with ID " + staffId + " not found.");
        }

        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("Name cannot be empty.");
        }

        if (department == null || department.trim().isEmpty()) {
            throw new InvalidInputException("Department cannot be empty.");
        }

        String cleanPhone = phone != null ? phone.trim() : "";

        if (mySQLUserRepository != null) {
            return mySQLUserRepository.updateStaffProfile(staffId.trim(), name.trim(), department.trim(), cleanPhone);
        } else {
            user.setName(name.trim());
            if (user instanceof Staff) {
                ((Staff) user).setDepartment(department.trim());
            }
            user.setPhone(cleanPhone);
            userRepository.saveUser(user);
            return true;
        }
    }

    /**
     * Updates a staff member's active status (ACTIVE or INACTIVE).
     *
     * @param staffId Staff User ID
     * @param status New status ("ACTIVE" or "INACTIVE")
     * @return true if status was updated
     * @throws UserNotFoundException if staff member not found
     * @throws InvalidInputException if status value is invalid
     */
    public boolean setStaffStatus(String staffId, String status)
            throws UserNotFoundException, InvalidInputException {

        if (staffId == null || staffId.trim().isEmpty()) {
            throw new InvalidInputException("Staff ID cannot be empty.");
        }

        if (status == null || (!status.trim().equalsIgnoreCase("ACTIVE") && !status.trim().equalsIgnoreCase("INACTIVE"))) {
            throw new InvalidInputException("Invalid status. Must be ACTIVE or INACTIVE.");
        }

        User user = findUserById(staffId.trim());
        if (user == null || !"STAFF".equalsIgnoreCase(user.getRole())) {
            throw new UserNotFoundException("Staff member with ID " + staffId + " not found.");
        }

        String normalizedStatus = status.trim().toUpperCase();

        if (mySQLUserRepository != null) {
            return mySQLUserRepository.updateStaffStatus(staffId.trim(), normalizedStatus);
        } else {
            user.setStatus(normalizedStatus);
            userRepository.saveUser(user);
            return true;
        }
    }

    /**
     * Authenticates a user using BCrypt password hashing.
     * Supports transparent password upgrade if legacy plaintext is detected.
     * Prevents deactivated accounts from logging in.
     *
     * @param username Account username
     * @param password Account password (plaintext)
     * @return authenticated User object
     * @throws UserNotFoundException if username is not in database
     * @throws InvalidCredentialsException if password is incorrect or account is inactive
     */
    public User login(String username, String password)
            throws UserNotFoundException, InvalidCredentialsException {

        if (username == null || username.trim().isEmpty()) {
            throw new UserNotFoundException("Username cannot be empty.");
        }

        User user = findUserByUsername(username.trim());

        if (user == null) {
            throw new UserNotFoundException("User with username " + username + " not found.");
        }

        String storedPassword = user.getPassword();

        // Verify with BCrypt (with legacy plaintext fallback support)
        boolean matches = PasswordUtil.checkPassword(password, storedPassword);

        if (!matches) {
            throw new InvalidCredentialsException("Incorrect password.");
        }

        // Account status check: deactivated users cannot log in
        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is deactivated. Please contact the administrator.");
        }

        // Transparent migration: if stored password was plaintext, upgrade to BCrypt in MySQL
        if (mySQLUserRepository != null && !PasswordUtil.isBCryptHash(storedPassword)) {
            mySQLUserRepository.updatePassword(user.getUserId(), password);
        }

        return user;
    }
}
