package com.lunchbox.delivery.models;

/**
 * Delivery model — updated with adminPhone field
 *
 * New field: adminPhone
 *   Stored in each delivery document when assigned.
 *   Copied from the admin's users document at assignment time.
 *   Allows agents to call admin directly from the delivery card.
 */
public class Delivery {
    private String deliveryId;
    private String customerName;
    private String customerPhone;
    private String pickupLocation;
    private String deliveryAddress;
    private String status;
    private String assignedTo;
    private String assignedName;
    private long   timestamp;
    private String notes;
    private int    itemCount;
    private int    pickupOrder;
    private String deliveryDate;
    private String adminPhone;   // NEW — admin phone for "Call Admin" button

    public Delivery() {}

    // ── Getters ──
    public String getDeliveryId()     { return deliveryId; }
    public String getCustomerName()   { return customerName; }
    public String getCustomerPhone()  { return customerPhone; }
    public String getPickupLocation() { return pickupLocation; }
    public String getDeliveryAddress(){ return deliveryAddress; }
    public String getStatus()         { return status; }
    public String getAssignedTo()     { return assignedTo; }
    public String getAssignedName()   { return assignedName; }
    public long   getTimestamp()      { return timestamp; }
    public String getNotes()          { return notes; }
    public int    getItemCount()      { return itemCount; }
    public int    getPickupOrder()    { return pickupOrder; }
    public String getDeliveryDate()   { return deliveryDate; }
    public String getAdminPhone()     { return adminPhone; }

    // ── Setters ──
    public void setDeliveryId(String v)     { deliveryId = v; }
    public void setCustomerName(String v)   { customerName = v; }
    public void setCustomerPhone(String v)  { customerPhone = v; }
    public void setPickupLocation(String v) { pickupLocation = v; }
    public void setDeliveryAddress(String v){ deliveryAddress = v; }
    public void setStatus(String v)         { status = v; }
    public void setAssignedTo(String v)     { assignedTo = v; }
    public void setAssignedName(String v)   { assignedName = v; }
    public void setTimestamp(long v)        { timestamp = v; }
    public void setNotes(String v)          { notes = v; }
    public void setItemCount(int v)         { itemCount = v; }
    public void setPickupOrder(int v)       { pickupOrder = v; }
    public void setDeliveryDate(String v)   { deliveryDate = v; }
    public void setAdminPhone(String v)     { adminPhone = v; }
}