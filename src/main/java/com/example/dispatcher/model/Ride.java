package com.example.dispatcher.model;

import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class Ride {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private final String id;
    @NotNull(message = "Pickup location is required")
    private Location pickup;
    @NotNull(message = "Drop location is required")
    private Location drop;

    private RideStatus status = RideStatus.REQUESTED;

    private String assignedDriverId;

    // drivers already pinged for this ride
    private Set<String> pingedDrivers = new HashSet<>();

    // business flag only
    private boolean cancelledAfterAccept;

    private List<String> timers = new ArrayList<>();

    private final ReentrantLock lock = new ReentrantLock();
    public Ride() {
        this.id = "R-" + SEQ.getAndIncrement();
    }

    // ---------- getters & setters ----------

    public String getId() {
        return id;
    }

    public Location getPickup() {
        return pickup;
    }

    public void setPickup(Location pickup) {
        this.pickup = pickup;
    }

    public Location getDrop() {
        return drop;
    }

    public void setDrop(Location drop) {
        this.drop = drop;
    }

    public RideStatus getStatus() {
        return status;
    }

    public void setStatus(RideStatus status) {
        this.status = status;
    }

    public String getAssignedDriverId() {
        return assignedDriverId;
    }

    public void setAssignedDriverId(String assignedDriverId) {
        this.assignedDriverId = assignedDriverId;
    }

    public Set<String> getPingedDrivers() {
        return pingedDrivers;
    }

    public boolean isCancelledAfterAccept() {
        return cancelledAfterAccept;
    }

    public void markCancelledAfterAccept() {
        this.cancelledAfterAccept = true;
    }

    public List<String> getTimers() {
        return timers;
    }

    public boolean tryLock(long timeoutMs) throws InterruptedException {
        return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

}
