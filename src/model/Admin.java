package model;

public class Admin extends User {

    public Admin(String userId,
                 String name,
                 String username,
                 String password) {

        super(userId, name, username, password);
    }

    public Admin(String userId,
                 String name,
                 String username,
                 String password,
                 String status,
                 String phone) {

        super(userId, name, username, password, status, phone);
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }
}
