package com.example.dispatcher.service;

import com.example.dispatcher.geo.GeoHashUtil;
import com.example.dispatcher.lock.LockPolicy;
import com.example.dispatcher.model.*;
import com.example.dispatcher.state.RideStateMachine;
import com.example.dispatcher.store.GeoDriverStore;
import com.example.dispatcher.store.InMemoryStore;
import com.example.dispatcher.timer.TimerManager;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.*;

@Service
public class DispatchService {

    private final GeoDriverStore geoStore;
    private final InMemoryStore store;
    private final TimerManager timerManager;

    public DispatchService(
            GeoDriverStore geoStore,
            InMemoryStore store,
            TimerManager timerManager
    ) {
        this.geoStore = geoStore;
        this.store = store;
        this.timerManager = timerManager;
    }

    // ---------------- DISPATCH ----------------

//    public void dispatch(Ride ride) {
//
//        // Dispatch allowed only in waiting states
//        if (ride.getStatus() != RideStatus.REQUESTED &&
//                ride.getStatus() != RideStatus.DRIVER_PINGED) {
//            return;
//        }
//
//        String pickupHash = GeoHashUtil.encode(
//                ride.getPickup().lat(),
//                ride.getPickup().lng()
//        );
//
//        Set<String> searchHashes = new HashSet<>();
//        searchHashes.add(pickupHash);
//        searchHashes.addAll(GeoHashUtil.neighbors(pickupHash));
//        System.out.println(searchHashes);
//        List<Driver> candidates =
//                geoStore.find(searchHashes).stream()
//                        .map(gd -> store.drivers.get(gd.getDriverId()))
//                        .filter(Objects::nonNull)
//                        .filter(d -> d.getStatus() == DriverStatus.ONLINE)
//                        .filter(d -> !ride.getPingedDrivers().contains(d.getId()))
//                        .sorted(Comparator.comparingDouble(
//                                d -> distance(d.getLocation(), ride.getPickup())
//                        ))
//                        .toList();
//        System.out.println(candidates);
//        if (candidates.isEmpty()) {
//            ride.setStatus(RideStatus.CANCELLED);
//            ride.setAssignedDriverId(null);
//            return;
//        }
//
//        Driver selected = candidates.get(0);
//
//        ride.setAssignedDriverId(selected.getId());
//        ride.getPingedDrivers().add(selected.getId());
//        ride.setStatus(RideStatus.DRIVER_PINGED);
//
//        // schedule timeout OUTSIDE domain
//        timerManager.scheduleTimeout(
//                ride.getId(),
//                selected.getId(),
//                () -> onTimeout(ride, selected)
//        );
//    }


    // ---------------- TIMEOUT HANDLER ----------------

    private void onTimeout(Ride ride, Driver driver) {

        boolean rideLocked = false;
        boolean driverLocked = false;

        try {
            // 🔧 ADD: fail-fast locking
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) return;

            driverLocked = driver.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!driverLocked) return;

            // ignore stale timeout
        if (ride.getStatus() != RideStatus.DRIVER_PINGED) {
            return;
        }

        driver.recordTimeout();
            String key = ride.getId() + ":" + driver.getId();
            store.rideTimerExpired.put(key, true);
            // CHANGE: state back to REQUESTED (spec)
        RideStateMachine.validate(ride.getStatus(), RideStatus.REQUESTED);
        ride.setStatus(RideStatus.REQUESTED);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (driverLocked) driver.unlock();
            if (rideLocked) ride.unlock();
        }
        // retry dispatch
        dispatch(ride);
    }

    // ---------------- UTILITY ----------------

    private double distance(Location a, Location b) {
        return Math.hypot(a.lat() - b.lat(), a.lng() - b.lng());
    }

    public void dispatch(Ride ride) {
        boolean rideLocked = false;
        try {
            // 🔧 ADD: tryLock
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) return;

            // 1️⃣ Dispatch allowed only in waiting states
            if (ride.getStatus() != RideStatus.REQUESTED &&
                    ride.getStatus() != RideStatus.DRIVER_PINGED) {
                return;
            }

            // 2️⃣ Encode pickup location
            String pickupHash = GeoHashUtil.encode(
                    ride.getPickup().lat(),
                    ride.getPickup().lng()
            );

            Driver nearest = null;                 // ✅ FIX 1
            double nearestDistance = Double.MAX_VALUE;

            final int MAX_RINGS = 30;

            // 3️⃣ Progressive ring expansion
            for (int ring = 0; ring <= MAX_RINGS; ring++) {

                Set<String> searchHashes = GeoHashUtil.neighbors(pickupHash, ring);

                List<Driver> candidates =
                        geoStore.find(searchHashes).stream()
                                .map(gd -> store.drivers.get(gd.getDriverId()))
                                .filter(Objects::nonNull)
                                .filter(d -> d.getStatus() == DriverStatus.ONLINE)
                                .filter(d -> !ride.getPingedDrivers().contains(d.getId()))
                                .toList();

                boolean foundInThisRing = false;

                // 4️⃣ Find nearest driver in THIS ring
                for (Driver d : candidates) {

                    double dist = GeoHashUtil.distanceMeters(
                            d.getLocation().lat(),
                            d.getLocation().lng(),
                            ride.getPickup().lat(),
                            ride.getPickup().lng()
                    );

                    if (dist < nearestDistance) {
                        nearestDistance = dist;
                        nearest = d;
                        foundInThisRing = true;
                    }
                }

                // 5️⃣ Early exit ONLY if this ring had candidates
                if (foundInThisRing) {
                    break;
                }
            }

            // 6️⃣ No driver found after all rings
            if (nearest == null) {
                ride.setStatus(RideStatus.REQUESTED);
                return;
            }

            boolean driverLocked = false;
            try {
                // 🔧 ADD: lock driver (Ride → Driver order)
                driverLocked = nearest.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
                if (!driverLocked) return;

                RideStateMachine.validate(
                        ride.getStatus(),
                        RideStatus.DRIVER_PINGED
                );

                ride.getPingedDrivers().add(nearest.getId());
                ride.setStatus(RideStatus.DRIVER_PINGED);

                String key = ride.getId() + ":" + nearest.getId();
                store.rideTimerExpired.put(key, false);
                // 8️⃣ Schedule timeout (outside domain logic)
                Driver finalNearest = nearest;
                String timerId = timerManager.schedule(
                        ride.getId(),
                        "DRIVER_TIMEOUT",
                        20,
                        () -> onTimeout(ride, finalNearest)
                );

// 🔧             CHANGE: ride owns timer
                ride.getTimers().add(timerId);
            } finally {
                if (driverLocked) nearest.unlock(); // 🔧 ADD
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (rideLocked) ride.unlock(); // 🔧 ADD
        }

    }
}
