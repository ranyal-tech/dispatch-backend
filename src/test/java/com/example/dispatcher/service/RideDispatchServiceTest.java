package com.example.dispatcher.service;

import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.DriverStatus;
import com.example.dispatcher.model.Location;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.model.RideStatus;
import com.example.dispatcher.store.GeoDriverStore;
import com.example.dispatcher.store.InMemoryStore;
import com.example.dispatcher.timer.TimerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RideDispatchServiceTest {

    private InMemoryStore store;
    private GeoDriverStore geoStore;
    private DispatchService dispatchService;
    private RideService service;
    private TimerManager timerManager;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        geoStore = new GeoDriverStore();
        timerManager = new TimerManager();
        dispatchService = new DispatchService(geoStore, store, timerManager);
        service = new RideService(store, dispatchService, timerManager);
    }

    @Test
    void shouldPingNearestOnlineDriver() {
        Driver d1 = addDriver( 28.6315, 77.2167, DriverStatus.ONLINE);
        Driver d2 = addDriver( 28.6517, 77.1900, DriverStatus.ONLINE);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.6320, 77.2170));
        
        Ride createdRide = service.create(ride);
        
        assertEquals(RideStatus.DRIVER_PINGED, createdRide.getStatus());
        assertTrue(createdRide.getPingedDrivers().contains(d1.getId()));
        // Note: dispatch currently doesn't set assignedDriverId on the Ride object during ping
        assertNull(createdRide.getAssignedDriverId());
    }

    @Test
    void shouldLockRideAfterAccept() {
       Driver d1= addDriver( 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = new Ride();

        ride.setPickup(new Location(28.631, 77.215));
        service.create(ride);

        service.accept(ride.getId(), d1.getId());

        Ride updated = service.getRide(ride.getId());

        assertEquals(RideStatus.ACCEPTED, updated.getStatus());
        assertEquals(d1.getId(), updated.getAssignedDriverId());
        assertEquals(ride.getId(), store.drivers.get(d1.getId()).getAssignedRideId());
    }

    @Test
    void shouldPreventDoubleAccept() {
        Driver d1=addDriver( 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = new Ride();

        ride.setPickup(new Location(28.63, 77.21));
        service.create(ride);

        service.accept(ride.getId(), d1.getId());

        assertThrows(IllegalStateException.class,
                () -> service.accept(ride.getId(), d1.getId()));
    }


    @Test
    void riderCancelAfterAcceptReleasesDriver() {
        Driver D1=addDriver( 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = new Ride();

        ride.setPickup(new Location(28.63, 77.21));
        service.create(ride);

        service.accept(ride.getId(), D1.getId());

        service.riderCancel(ride.getId());

        Ride updatedRide = service.getRide(ride.getId());
        Driver driver = store.drivers.get(D1.getId());

        assertEquals(RideStatus.CANCELLED, updatedRide.getStatus());
        assertNull(driver.getAssignedRideId());
    }

    @Test
    void driverCancelTriggersRedispatch() {
        Driver D1=addDriver( 28.63, 77.21, DriverStatus.ONLINE);
        Driver D2=addDriver( 28.64, 77.22, DriverStatus.ONLINE);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.63, 77.21));
        service.create(ride);

        service.accept(ride.getId(), D1.getId());

        service.driverCancel(ride.getId(), D1.getId());

        Ride updated = service.getRide(ride.getId());

        // After driver cancel, it transitions to REQUESTED and starts re-dispatch
        assertEquals(RideStatus.DRIVER_PINGED, updated.getStatus());
        assertTrue(updated.getPingedDrivers().contains(D2.getId()));
    }

    // ---------------- HELPER ----------------
    private Driver addDriver( double lat, double lng, DriverStatus status) {
        Driver d = new Driver();
        d.updateLocation(new Location(lat, lng));
        d.setStatus(status);

        store.drivers.put(d.getId(), d);
        geoStore.addOrUpdate(d);

        return d;
    }

    @Test
    void shouldNotDispatchIfNoOnlineDrivers() {

        Ride ride = new Ride();
        ride.setPickup(new Location(28.63, 77.21));
        service.create(ride);
        assertEquals(RideStatus.REQUESTED, ride.getStatus());
    }

    @Test
    void driverCannotCancelAfterArriving() {

       Driver d1= addDriver( 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.63, 77.21));
        service.create(ride);
        service.accept(ride.getId(), d1.getId());

        // simulate ARRIVING
        service.transitionToArriving(
                service.getRide(ride.getId()));

        assertThrows(IllegalStateException.class,
                () -> service.driverCancel(ride.getId(), d1.getId()));
    }

}
