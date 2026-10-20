package model;

public class Student extends User {

    private String roomNumber;

    public Student(String userId,
                   String name,
                   String username,
                   String password,
                   String roomNumber) {

        super(userId, name, username, password);
        this.roomNumber = roomNumber;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    @Override
    public String getRole() {
        return "STUDENT";
    }
}