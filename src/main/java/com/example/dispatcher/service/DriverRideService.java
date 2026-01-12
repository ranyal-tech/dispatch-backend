package com.example.dispatcher.service;

import com.example.dispatcher.model.DriverPingStatusResponse;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.store.InMemoryStore;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;

@Service
public class DriverRideService {

    private final InMemoryStore store;

    public DriverRideService(InMemoryStore store) {
        this.store = store;
    }

    public List<DriverPingStatusResponse> getAllRidesForDriver(String driverId) {

        List<DriverPingStatusResponse> result = new ArrayList<>();

        for (Ride ride : store.rides.values()) {

            boolean pinged = ride.getPingedDrivers().contains(driverId);
            boolean assigned = driverId.equals(ride.getAssignedDriverId());

            // if driver was never involved → skip
            if (!pinged && !assigned) {
                continue;
            }

            DriverPingStatusResponse res = new DriverPingStatusResponse();
            res.setRideId(ride.getId());
            res.setDriverId(driverId);
            res.setPinged(pinged);
            res.setCurrentlyAssigned(assigned);
            res.setRideStatus(ride.getStatus());
            res.setPickup(ride.getPickup());
            res.setDrop(ride.getDrop());
            // expired flag
            String key = ride.getId() + ":" + driverId;
            res.setExpired(
                    store.rideTimerExpired.getOrDefault(key, false)

            );

            result.add(res);
        }

        return result;
    }
}
