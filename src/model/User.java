package model;

public abstract class User {

    private String userId;
    private String name;
    private String username;
    private String password;
    private String status;
    private String phone;

    public User(String userId, String name,
                String username, String password) {
        this(userId, name, username, password, "ACTIVE", "");
    }

    public User(String userId, String name,
                String username, String password,
                String status, String phone) {
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.password = password;
        this.status = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : "ACTIVE";
        this.phone = (phone != null) ? phone.trim() : "";
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getStatus() {
        return status != null ? status : "ACTIVE";
    }

    public void setStatus(String status) {
        this.status = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : "ACTIVE";
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(getStatus());
    }

    public void setActive(boolean active) {
        this.status = active ? "ACTIVE" : "INACTIVE";
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = (phone != null) ? phone.trim() : "";
    }

    public abstract String getRole();
}
