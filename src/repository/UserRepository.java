package repository;

import model.User;

import java.util.ArrayList;

public interface UserRepository {

    void saveUser(User user);

    ArrayList<User> loadUsers();
}