package com.example.dispatcher.model;

import com.example.dispatcher.geo.GeoHashUtil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class Driver {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private final String id;                 // 🔒 immutable identity
    private Location location;
    private String geoHash;
    private DriverStatus status = DriverStatus.OFFLINE;

    private double rating;
    private long lastStateChangeAt;
    private int rejectCount;
    private int timeoutCount;
    private String assignedRideId;
    private final ReentrantLock lock = new ReentrantLock();

    public Driver() {
        this.id = "D-" + SEQ.getAndIncrement();   // ✅ ID generated once
        this.lastStateChangeAt = System.currentTimeMillis();
    }

    // 🔒 ONLY way to update location
    public void updateLocation(Location location) {
        this.location = location;
        this.geoHash = GeoHashUtil.encode(location.lat(), location.lng());
    }

    // 🔒 domain events (increment only)
    public void recordReject() {
        rejectCount++;
    }

    public void recordTimeout() {
        timeoutCount++;
    }

    // ---------------- GETTERS ----------------

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public String getGeoHash() {
        return geoHash;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public long getLastStateChangeAt() {
        return lastStateChangeAt;
    }

    public int getRejectCount() {
        return rejectCount;
    }

    public int getTimeoutCount() {
        return timeoutCount;
    }

    // 🔒 SINGLE ENTRY POINT for state change
    public void setStatus(DriverStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            this.lastStateChangeAt = System.currentTimeMillis();
        }
    }

    public boolean tryLock(long timeoutMs) throws InterruptedException {
        return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public void assignRide(String rideId) {
        this.assignedRideId = rideId;
    }

    public void clearAssignedRide() {
        this.assignedRideId = null;
        setStatus(DriverStatus.ONLINE);
    }

    public String getAssignedRideId() {
        return assignedRideId;
    }

}
