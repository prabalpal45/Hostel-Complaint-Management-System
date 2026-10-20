package service;

import exception.InvalidInputException;
import exception.InvalidCredentialsException;
import exception.UserNotFoundException;

import model.User;

import repository.FileManager;
import repository.UserRepository;

import java.util.ArrayList;

public class UserService {

    private UserRepository userRepository;

    public UserService() {
        userRepository = new FileManager();
    }

    public void registerUser(User user)
            throws InvalidInputException {

        if (user.getUserId() == null ||
                user.getUserId().trim().isEmpty()) {

            throw new InvalidInputException(
                    "User ID cannot be empty."
            );
        }

        if (findUserById(user.getUserId().trim()) != null) {

            throw new InvalidInputException(
                    "User ID already exists."
            );
        }

        if (user.getName() == null ||
                user.getName().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Name cannot be empty."
            );
        }

        if (user.getUsername() == null ||
                user.getUsername().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Username cannot be empty."
            );
        }

        if (user.getPassword() == null ||
                user.getPassword().trim().isEmpty()) {

            throw new InvalidInputException(
                    "Password cannot be empty."
            );
        }
        if (findUserByUsername(user.getUsername().trim()) != null) {

            throw new InvalidInputException(
                    "Username already exists."
            );
        }

        userRepository.saveUser(user);

        System.out.println(
                "User registered successfully."
        );
    }

    public User findUserById(String userId) {

        if (userId == null) {
            return null;
        }

        ArrayList<User> users = userRepository.loadUsers();

        for (User user : users) {

            if (user.getUserId().equalsIgnoreCase(userId.trim())) {
                return user;
            }
        }

        return null;
    }

    public User findUserByUsername(String username) {

        if (username == null) {
            return null;
        }

        ArrayList<User> users = userRepository.loadUsers();

        for (User user : users) {

            if (user.getUsername().equalsIgnoreCase(username.trim())) {
                return user;
            }
        }

        return null;
    }

    public ArrayList<User> getAllUsers() {
        return userRepository.loadUsers();
    }

    public ArrayList<model.Student> getStudents() {
        ArrayList<model.Student> list = new ArrayList<>();
        for (User u : userRepository.loadUsers()) {
            if (u instanceof model.Student) {
                list.add((model.Student) u);
            }
        }
        return list;
    }

    public ArrayList<model.Staff> getStaffList() {
        ArrayList<model.Staff> list = new ArrayList<>();
        for (User u : userRepository.loadUsers()) {
            if (u instanceof model.Staff) {
                list.add((model.Staff) u);
            }
        }
        return list;
    }

    public User     login(String username, String password)
            throws UserNotFoundException,
            InvalidCredentialsException {

        User user = findUserByUsername(username);

        if (user == null) {

            throw new UserNotFoundException(
                    "User with username " +
                            username +
                            " not found."
            );
        }

        if (!user.getPassword().equals(password)) {

            throw new InvalidCredentialsException(
                    "Incorrect password."
            );
        }

        return user;
    }

}