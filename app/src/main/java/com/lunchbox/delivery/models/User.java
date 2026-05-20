package com.lunchbox.delivery.models;

public class User {
    private String userId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String fcmToken;
    private String photoUrl;
    private int totalDeliveries;
    private int completedDeliveries;
    private boolean active;

    public User() {}

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getFcmToken() { return fcmToken; }
    public String getPhotoUrl() { return photoUrl; }
    public int getTotalDeliveries() { return totalDeliveries; }
    public int getCompletedDeliveries() { return completedDeliveries; }
    public boolean isActive() { return active; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setRole(String role) { this.role = role; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setTotalDeliveries(int totalDeliveries) { this.totalDeliveries = totalDeliveries; }
    public void setCompletedDeliveries(int completedDeliveries) { this.completedDeliveries = completedDeliveries; }
    public void setActive(boolean active) { this.active = active; }
}