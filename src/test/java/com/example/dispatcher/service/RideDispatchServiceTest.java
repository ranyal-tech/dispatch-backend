package com.example.dispatcher.service;

import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.DriverStatus;
import com.example.dispatcher.model.Location;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.model.RideStatus;
import com.example.dispatcher.store.GeoDriverStore;
import com.example.dispatcher.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RideDispatchServiceTest {

    private InMemoryStore store;
    private GeoDriverStore geoStore;
    private RideDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        geoStore = new GeoDriverStore();
        dispatchService = new RideDispatchService(store, geoStore);
    }

    @Test
    void shouldAssignRideToNearestOnlineDriver() {
        addDriver("D1", 28.6315, 77.2167, DriverStatus.ONLINE);
        addDriver("D2", 28.6517, 77.1900, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R1", new Location(28.6320, 77.2170));

        assertEquals(RideStatus.DRIVER_PINGED, ride.getStatus());
        assertEquals("D1", ride.getAssignedDriverId());
    }

    @Test
    void shouldMoveToNextDriverOnReject() {
        addDriver("D1", 28.63, 77.21, DriverStatus.ONLINE);
        addDriver("D2", 28.64, 77.22, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R2", new Location(28.631, 77.215));

        dispatchService.reject("R2", "D1");

        Ride updated = dispatchService.getRide("R2");
        assertEquals("D2", updated.getAssignedDriverId());
        assertEquals(RideStatus.DRIVER_PINGED, updated.getStatus());
    }

    @Test
    void shouldFailIfWrongDriverAccepts() {
        addDriver("D1", 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R3", new Location(28.631, 77.215));

        assertThrows(IllegalStateException.class,
                () -> dispatchService.accept("R3", "D2"));
    }

    @Test
    void shouldLockRideAfterAccept() {
        addDriver("D1", 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R4", new Location(28.631, 77.215));

        dispatchService.accept("R4", "D1");

        Ride updated = dispatchService.getRide("R4");
        assertEquals(RideStatus.ACCEPTED, updated.getStatus());
        assertEquals(DriverStatus.ON_TRIP,
                store.drivers.get("D1").getStatus());
    }

    @Test
    void shouldNotDispatchIfNoOnlineDrivers() {
        Ride ride = dispatchService.createRide(
                "R5", new Location(28.63, 77.21));

        assertEquals(RideStatus.CANCELLED, ride.getStatus());
    }

    @Test
    void shouldPreventDoubleAccept() {
        addDriver("D1", 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R6", new Location(28.63, 77.21));

        dispatchService.accept("R6", "D1");

        assertThrows(IllegalStateException.class,
                () -> dispatchService.accept("R6", "D1"));
    }

    // ---------------- HELPER ----------------

    private void addDriver(String id, double lat, double lng, DriverStatus status) {
        Driver d = new Driver();
        d.updateLocation(new Location(lat, lng));
        d.setStatus(status);
        d.updateLocation(d.getLocation());

        store.drivers.put(id, d);
        geoStore.addOrUpdate(d);
    }

    @Test
    void shouldRetryWithNextDriverOnTimeout() {

        addDriver("D1", 28.63, 77.21, DriverStatus.ONLINE);
        addDriver("D2", 28.64, 77.22, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R_TIMEOUT",
                new Location(28.631, 77.215)
        );

        // Initially pinged to nearest driver
        assertEquals("D1", ride.getAssignedDriverId());
        assertEquals(RideStatus.DRIVER_PINGED, ride.getStatus());

        // 🔥 simulate timeout
        dispatchService.onTimeout("R_TIMEOUT");

        Ride updated = dispatchService.getRide("R_TIMEOUT");

        assertEquals("D2", updated.getAssignedDriverId());
        assertEquals(RideStatus.DRIVER_PINGED, updated.getStatus());

        Driver d1 = store.drivers.get("D1");
        assertEquals(1, d1.getTimeoutCount());
        assertEquals(DriverStatus.ONLINE, d1.getStatus());
    }

    @Test
    void shouldRetryWithNextDriverOnReject() {

        addDriver("D1", 28.63, 77.21, DriverStatus.ONLINE);
        addDriver("D2", 28.64, 77.22, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R_REJECT",
                new Location(28.631, 77.215)
        );

        assertEquals("D1", ride.getAssignedDriverId());

        dispatchService.reject("R_REJECT", "D1");

        Ride updated = dispatchService.getRide("R_REJECT");

        assertEquals("D2", updated.getAssignedDriverId());
        assertEquals(RideStatus.DRIVER_PINGED, updated.getStatus());

        Driver d1 = store.drivers.get("D1");
        assertEquals(1, d1.getRejectCount());
        assertEquals(DriverStatus.ONLINE, d1.getStatus());
    }

    @Test
    void shouldRejectInvalidStateTransitions() {

        addDriver("D1", 28.63, 77.21, DriverStatus.ONLINE);

        Ride ride = dispatchService.createRide(
                "R_STATE",
                new Location(28.631, 77.215)
        );

        // ✅ valid accept
        dispatchService.accept("R_STATE", "D1");

        Ride accepted = dispatchService.getRide("R_STATE");
        assertEquals(RideStatus.ACCEPTED, accepted.getStatus());

        // ❌ reject after accept
        assertThrows(IllegalStateException.class,
                () -> dispatchService.reject("R_STATE", "D1"));

        // ❌ accept again
        assertThrows(IllegalStateException.class,
                () -> dispatchService.accept("R_STATE", "D1"));

        // ❌ timeout after accept
        assertThrows(IllegalStateException.class,
                () -> dispatchService.onTimeout("R_STATE"));
    }



}
