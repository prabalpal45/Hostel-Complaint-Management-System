package model;

public class Staff extends User {

    private String department;

    public Staff(String userId,
                 String name,
                 String username,
                 String password,
                 String department) {

        super(userId, name, username, password);
        this.department = department;
    }

    public String getDepartment() {
        return department;
    }

    @Override
    public String getRole() {
        return "STAFF";
    }
}