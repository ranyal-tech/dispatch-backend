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

    public DriverPingStatusResponse accept(String rideId, String driverId) {

        Ride ride = store.rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride not found: " + rideId);
        }

        Driver driver = store.drivers.get(driverId);
        if (driver == null) {
            throw new IllegalArgumentException("Driver not found: " + driverId);
        }

        boolean rideLocked = false;
        boolean driverLocked = false;

        try {
            // 🔐 Lock ride first (global order)
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) {
                throw new IllegalStateException("Could not lock ride");
            }

            // ✅ Accept allowed only while pinged
            if (ride.getStatus() != RideStatus.DRIVER_PINGED) {
                throw new IllegalStateException(
                        "Ride not in DRIVER_PINGED state: " + ride.getStatus()
                );
            }

            // ✅ Driver must have been pinged
            if (!ride.getPingedDrivers().contains(driverId)) {
                throw new IllegalStateException(
                        "Driver was not pinged for this ride"
                );
            }

            // 🔐 Lock driver second
            driverLocked = driver.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!driverLocked) {
                throw new IllegalStateException("Could not lock driver");
            }

            // ✅ Driver must be free
            if (driver.getAssignedRideId() != null) {
                throw new IllegalStateException(
                        "Driver already assigned to another ride"
                );
            }

            // 🔄 Clear all ping / timeout timers
            clearRideTimers(ride);

            // 🎯 ATOMIC COMMIT (first-accept-wins)
            ride.setAssignedDriverId(driverId);
            driver.assignRide(rideId);

            transition(ride, RideStatus.ACCEPTED);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (driverLocked) driver.unlock();
            if (rideLocked) ride.unlock();
        }

        // ⏱ Start ARRIVING timer outside locks
        scheduleArriving(ride);

        // 📦 Response
        DriverPingStatusResponse res = new DriverPingStatusResponse();
        res.setRideId(rideId);
        res.setDriverId(driverId);
        res.setPinged(true);
        res.setCurrentlyAssigned(true);
        res.setRideStatus(ride.getStatus());
        res.setExpired(false);
        res.setPickup(ride.getPickup());
        res.setDrop(ride.getDrop());
        return res;
    }

    public String riderCancel(String rideId) {

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

                        //  ADD: clear bidirectional relationship
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
            if (driverLocked) driver.unlock();
            if (rideLocked) ride.unlock();
        }
        return "Ride has been cancelled by Rider";
    }

    public String driverCancel(String rideId, String driverId) {

        Ride ride = store.rides.get(rideId);
        Driver driver = store.drivers.get(driverId);

        if (ride == null || driver == null) {
            throw new IllegalArgumentException("Ride or Driver not found");
        }

        boolean rideLocked = false;
        boolean driverLocked = false;

        try {
            // Lock order: Ride → Driver (consistent everywhere)
            rideLocked = ride.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!rideLocked) throw new IllegalStateException("Could not lock ride");

            // Driver can cancel only before ARRIVING
            if (ride.getStatus() == RideStatus.ARRIVING ||
                    ride.getStatus() == RideStatus.ON_TRIP ||
                    ride.getStatus() == RideStatus.COMPLETED) {
                throw new IllegalStateException(
                        "Driver cannot cancel in state: " + ride.getStatus()
                );
            }

            if (!ride.getPingedDrivers().contains(driverId))  {
                throw new IllegalStateException("Driver not assigned to this ride");
            }

            driverLocked = driver.tryLock(LockPolicy.LOCK_TIMEOUT_MS);
            if (!driverLocked) throw new IllegalStateException("Could not lock driver");

            clearRideTimers(ride);

            // Reset assignments
            ride.setAssignedDriverId(null);
            driver.clearAssignedRide();
            String key = ride.getId() + ":" + driver.getId();
            store.rideTimerExpired.put(key, true);

            // Move ride back to REQUESTED
            transition(ride, RideStatus.REQUESTED);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (driverLocked) driver.unlock();
            if (rideLocked) ride.unlock();
        }

        // 🚀 Restart dispatch OUTSIDE locks
        dispatchService.dispatch(ride);

        return "Ride cancelled by driver, redispatch started";
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


    public void transitionToArriving(Ride ride) {

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
