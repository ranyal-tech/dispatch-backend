package com.example.dispatcher.service;

import com.example.dispatcher.model.*;
import com.example.dispatcher.store.GeoDriverStore;
import com.example.dispatcher.store.InMemoryStore;

import java.util.Comparator;
import java.util.List;

public class RideDispatchService {

    private final InMemoryStore store;
    private final GeoDriverStore geoStore;

    public RideDispatchService(InMemoryStore store, GeoDriverStore geoStore) {
        this.store = store;
        this.geoStore = geoStore;
    }

    // ---------------- CREATE & DISPATCH ----------------


    public Ride createRide(String rideId, Location pickup) {

        Ride ride = new Ride();
        ride.setPickup(pickup);
        ride.setStatus(RideStatus.REQUESTED);

        store.rides.put(rideId, ride);

        dispatch(ride);
        return ride;
    }

    private void dispatch(Ride ride) {

        List<Driver> candidates = store.drivers.values().stream()
                .filter(d -> d.getStatus() == DriverStatus.ONLINE)
                .filter(d -> !ride.getPingedDrivers().contains(d.getId()))
                .sorted(Comparator.comparingDouble(
                        d -> distance(d.getLocation(), ride.getPickup())
                ))
                .toList();

        if (candidates.isEmpty()) {
            ride.setStatus(RideStatus.CANCELLED);
            return;
        }

        Driver selected = candidates.get(0);

        ride.setAssignedDriverId(selected.getId());
        ride.getPingedDrivers().add(selected.getId());
        ride.setStatus(RideStatus.DRIVER_PINGED);
        // driver stays ONLINE until accept
    }

    // ---------------- ACCEPT / REJECT ----------------

    public void accept(String rideId, String driverId) {

        Ride ride = getRide(rideId);

        if (ride.getStatus() != RideStatus.DRIVER_PINGED) {
            throw new IllegalStateException("Ride not in DRIVER_PINGED state");
        }

        if (!driverId.equals(ride.getAssignedDriverId())) {
            throw new IllegalStateException("Driver not assigned to this ride");
        }

        Driver driver = store.drivers.get(driverId);

        ride.setStatus(RideStatus.ACCEPTED);
        driver.setStatus(DriverStatus.ON_TRIP);
    }

    public void reject(String rideId, String driverId) {

        Ride ride = getRide(rideId);

        if (ride.getStatus() != RideStatus.DRIVER_PINGED) {
            throw new IllegalStateException("Ride not in DRIVER_PINGED state");
        }

        if (!driverId.equals(ride.getAssignedDriverId())) {
            throw new IllegalStateException("Driver not assigned to this ride");
        }

        Driver driver = store.drivers.get(driverId);
        driver.recordReject();
        driver.setStatus(DriverStatus.ONLINE);

        // retry dispatch
        dispatch(ride);
    }

    // ---------------- READ ----------------

    public Ride getRide(String rideId) {
        Ride ride = store.rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride not found");
        }
        return ride;
    }

    // ---------------- UTILITY ----------------

    private double distance(Location a, Location b) {
        double dx = a.lat() - b.lat();
        double dy = a.lng() - b.lng();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public void onTimeout(String rideId) {

        Ride ride = getRide(rideId);

        // timeout allowed only when driver is pinged
        if (ride.getStatus() != RideStatus.DRIVER_PINGED) {
            throw new IllegalStateException("Timeout not allowed in state " + ride.getStatus());
        }

        String driverId = ride.getAssignedDriverId();
        Driver driver = store.drivers.get(driverId);

        if (driver != null) {
            driver.recordTimeout();
            driver.setStatus(DriverStatus.ONLINE);
        }

        // retry dispatch with remaining drivers
        dispatch(ride);
    }

}
