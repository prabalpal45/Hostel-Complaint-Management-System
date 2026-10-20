package model;

public class Admin extends User {

    public Admin(String userId,
                 String name,
                 String username,
                 String password) {

        super(userId, name, username, password);
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }
}