package com.example.dispatcher.service;

import com.example.dispatcher.model.*;
import com.example.dispatcher.state.RideStateMachine;
import com.example.dispatcher.store.InMemoryStore;
import com.example.dispatcher.timer.TimerManager;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;

@Service
public class RideService {

    private final InMemoryStore store;
    private final DispatchService dispatchService;
    private final TimerManager timerManager;

    public RideService(InMemoryStore store,
                       DispatchService dispatchService,
                       TimerManager timerManager) {
        this.store = store;
        this.dispatchService = dispatchService;
        this.timerManager = timerManager;
    }

    public Ride create(Ride ride) {

        store.rides.put(ride.getId(), ride);
        dispatchService.dispatch(ride);
        return ride;
    }

    public void accept(String rideId) {
        Ride ride = store.rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride not found: " + rideId);
        }
        // 🔧 CHANGE: clear any existing timers (timeout)
        clearRideTimers(ride);
        RideStateMachine.validate(ride.getStatus(), RideStatus.ACCEPTED);
        ride.setStatus(RideStatus.ACCEPTED);

        // 🔧 CHANGE: driver goes ON_TRIP
        Driver driver = store.drivers.get(ride.getAssignedDriverId());
        if (driver == null) {
            throw new IllegalArgumentException("Driver not found");
        }
        driver.setStatus(DriverStatus.ON_TRIP);

        // 🔧 CHANGE: start ride progression timers
        scheduleArriving(ride);
    }

    public void cancel(String rideId) {
        Ride ride = store.rides.get(rideId);
        // 🔧 CHANGE: stop all timers
        clearRideTimers(ride);
        if (ride.getStatus() == RideStatus.ACCEPTED) {
            ride.markCancelledAfterAccept();
            // 🔧 CHANGE: driver back ONLINE
            Driver driver = store.drivers.get(ride.getAssignedDriverId());
            driver.setStatus(DriverStatus.ONLINE);
        }

        RideStateMachine.validate(ride.getStatus(), RideStatus.CANCELLED);
        ride.setStatus(RideStatus.CANCELLED);
    }

    // 🔧 CHANGE: central transition helper
    private void transition(Ride ride, RideStatus next) {
        RideStateMachine.validate(ride.getStatus(), next);
        ride.setStatus(next);
    }

    // 🔧 CHANGE: central timer cleanup
    private void clearRideTimers(Ride ride) {
        for (String timerId : ride.getTimers()) {
            timerManager.clearTimer(timerId);
        }
        ride.getTimers().clear();
    }

    private void scheduleArriving(Ride ride) {

        String timerId = timerManager.schedule(
                ride.getId(),
                "ARRIVING",
                5,
                () -> transitionToArriving(ride)
        );

        // 🔧 CHANGE: ride owns this timer
        ride.getTimers().add(timerId);
    }

    private void transitionToArriving(Ride ride) {

        // 🔒 async safety
        if (ride.getStatus() != RideStatus.ACCEPTED) return;

        RideStateMachine.validate(ride.getStatus(), RideStatus.ARRIVING);
        ride.setStatus(RideStatus.ARRIVING);

        String timerId = timerManager.schedule(
                ride.getId(),
                "ON_TRIP",
                5,
                () -> transitionToOnTrip(ride)
        );

        ride.getTimers().add(timerId);
    }

    private void transitionToOnTrip(Ride ride) {

        if (ride.getStatus() != RideStatus.ARRIVING) return;

        RideStateMachine.validate(ride.getStatus(), RideStatus.ON_TRIP);
        ride.setStatus(RideStatus.ON_TRIP);

        String timerId = timerManager.schedule(
                ride.getId(),
                "COMPLETED",
                10,
                () -> completeRide(ride)
        );

        ride.getTimers().add(timerId);
    }

    private void completeRide(Ride ride) {

        if (ride.getStatus() != RideStatus.ON_TRIP) return;

        RideStateMachine.validate(ride.getStatus(), RideStatus.COMPLETED);
        ride.setStatus(RideStatus.COMPLETED);

        // 🔧 CHANGE: driver back ONLINE
        Driver driver = store.drivers.get(ride.getAssignedDriverId());
        driver.setStatus(DriverStatus.ONLINE);

        // 🔧 CHANGE: cleanup all timers
        clearRideTimers(ride);
    }

    public Ride getRide(String rideId) {

        Ride ride = store.rides.get(rideId);

        if (ride == null) {
            throw new IllegalArgumentException("Ride not found: " + rideId);
        }

        return ride;
    }

    public Collection<Ride> getAllRides() {
        return store.rides.values();
    }


    public DriverPingStatusResponse getPingStatus(String rideId, String driverId) {

        Ride ride = store.rides.get(rideId);
        Driver driver = store.drivers.get(driverId);

        if (ride == null || driver == null) {
            throw new IllegalArgumentException("Ride or Driver not found");
        }

        DriverPingStatusResponse response = new DriverPingStatusResponse();
        response.setRideId(rideId);
        response.setDriverId(driverId);
        response.setPinged(ride.getPingedDrivers().contains(driverId));
        response.setCurrentlyAssigned(
                driverId.equals(ride.getAssignedDriverId())
        );
        response.setRideStatus(ride.getStatus());

        return response;
    }


}
