package com.example.dispatcher.service;

import com.example.dispatcher.lock.LockPolicy;
import com.example.dispatcher.model.*;
import com.example.dispatcher.state.RideStateMachine;
import com.example.dispatcher.store.InMemoryStore;
import com.example.dispatcher.timer.RideTimingPolicy;
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

    public DriverPingStatusResponse accept(String rideId) {

        Ride ride = store.rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride not found: " + rideId);
        }

        boolean rideLocked = false;
        boolean driverLocked = false;
        Driver driver=null;
        try {
            // 🔧 ADD: fail-fast lock on ride
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) {
                throw new IllegalStateException("Could not lock ride");
            }

            // 🔧 ADD: state guard (idempotency)
            if (ride.getStatus() != RideStatus.DRIVER_PINGED) {
                throw new IllegalStateException(
                        "Ride not in DRIVER_PINGED state: " + ride.getStatus()
                );
            }

            String driverId = ride.getAssignedDriverId();
            if (driverId == null) {
                throw new IllegalStateException("No driver assigned to ride");
            }

             driver = store.drivers.get(driverId);
            if (driver == null) {
                throw new IllegalArgumentException("Driver not found: " + driverId);
            }

            // 🔧 ADD: lock driver (Ride → Driver ordering)
            driverLocked = driver.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!driverLocked) {
                throw new IllegalStateException("Could not lock driver");
            }

            // 🔧 ADD: double-accept protection
            if (driver.getAssignedRideId() != null &&
                    !rideId.equals(driver.getAssignedRideId())) {
                throw new IllegalStateException("Driver is not assigned");
            }

            // 🔧 CHANGE: clear all timers (timeout etc.)
            clearRideTimers(ride);

            // 🔧 CHANGE: enforce state machine

            transition(ride,RideStatus.ACCEPTED);
            // 🔧 CHANGE: establish bidirectional relationship
            driver.assignRide(ride.getId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (driverLocked) driver.unlock(); // 🔧 ADD
            if (rideLocked) ride.unlock();     // 🔧 ADD
        }

        // 🔧 CHANGE: start progression timers OUTSIDE locks
        scheduleArriving(ride);

        DriverPingStatusResponse res = new DriverPingStatusResponse();
        res.setRideId(ride.getId());
        res.setDriverId(driver.getId());
        res.setPinged(true);
        res.setCurrentlyAssigned(true);
        res.setRideStatus(ride.getStatus());
        res.setExpired(false);

        return res;

    }

    public String cancel(String rideId) {

        Ride ride = store.rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride not found: " + rideId);
        }

        boolean rideLocked = false;
        boolean driverLocked = false;
        Driver driver = null;

        try {
            // 🔧 ADD: lock ride (fail-fast)
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked)  throw new IllegalStateException("Could not lock ride");

            // 🔧 ADD: finalized rides are immutable
            if (ride.getStatus() == RideStatus.CANCELLED ||
                    ride.getStatus() == RideStatus.COMPLETED) {
                throw new IllegalStateException(
                        "Ride already finalized: " + ride.getStatus()
                );
            }

            // 🔧 CHANGE: stop all timers early
            clearRideTimers(ride);

            String driverId = ride.getAssignedDriverId();

            if (driverId != null) {
                 driver = store.drivers.get(driverId);

                if (driver != null) {
                    // 🔧 ADD: lock driver
                    driverLocked = driver.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
                    if (driverLocked) {

                        // 🔧 ADD: driver back ONLINE (YOUR LINE)
                        driver.setStatus(DriverStatus.ONLINE);

                        // 🔧 ADD: clear bidirectional relationship
                        driver.clearAssignedRide();
                    }
                }
            }

            // 🔧 CHANGE: mark business flag only if accepted
            if (ride.getStatus() == RideStatus.ACCEPTED) {
                ride.markCancelledAfterAccept();
            }

            // 🔧 CHANGE: finalize ride
            ride.setAssignedDriverId(null);
            transition(ride,RideStatus.CANCELLED);


        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (driverLocked) driver.unlock(); // 🔧 ADD
            if (rideLocked) ride.unlock();     // 🔧 ADD
        }
        return "Ride has been cancelled";
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

        boolean rideLocked = false;
        try {
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) return;

            String timerId = timerManager.schedule(
                    ride.getId(),
                    "ARRIVING",
                    RideTimingPolicy.ARRIVING_DELAY_SEC,
                    () -> transitionToArriving(ride)
            );

            ride.getTimers().add(timerId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (rideLocked) ride.unlock();
        }
    }


    private void transitionToArriving(Ride ride) {

        boolean rideLocked = false;
        try {
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) return;

        if (ride.getStatus() != RideStatus.ACCEPTED) return;
        transition(ride,RideStatus.ARRIVING);

        String timerId = timerManager.schedule(
                ride.getId(),
                "ON_TRIP",
                RideTimingPolicy.ON_TRIP_DELAY_SEC,
                () -> transitionToOnTrip(ride)
        );

        ride.getTimers().add(timerId);
    }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (rideLocked) ride.unlock();
        }
    }

    private void transitionToOnTrip(Ride ride) {

        boolean rideLocked = false;
        try {
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) return;

            if (ride.getStatus() != RideStatus.ARRIVING) return;

            transition(ride,RideStatus.ON_TRIP);
        String timerId = timerManager.schedule(
                ride.getId(),
                "COMPLETED",
                RideTimingPolicy.COMPLETE_DELAY_SEC,
                () -> completeRide(ride)
        );

        ride.getTimers().add(timerId);
    }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (rideLocked) ride.unlock();
        }
    }

    private void completeRide(Ride ride) {
        boolean rideLocked = false;
        try {
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) return;

            if (ride.getStatus() != RideStatus.ON_TRIP) return;


        transition(ride,RideStatus.COMPLETED);
        // 🔧 CHANGE: driver back ONLINE
        Driver driver = store.drivers.get(ride.getAssignedDriverId());

        driver.clearAssignedRide();

        //CHANGE: cleanup all timers
        clearRideTimers(ride);
        ride.getTimers().clear();
    }catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        if (rideLocked) ride.unlock();
    }
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
