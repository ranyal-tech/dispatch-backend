package com.example.dispatcher.service;

import com.example.dispatcher.model.*;
import com.example.dispatcher.store.GeoDriverStore;
import com.example.dispatcher.store.InMemoryStore;
import com.example.dispatcher.timer.TimerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchServiceTest {

    private GeoDriverStore geoStore;
    private TimerManager timerManager;
    private DispatchService dispatchService;
    private InMemoryStore store;
    private DriverService driverService;

    @BeforeEach
    void setup() {
        geoStore = new GeoDriverStore();
        timerManager = new TimerManager();
        store = new InMemoryStore();
        dispatchService = new DispatchService(geoStore,store, timerManager);
        driverService = new DriverService(store, geoStore);
    }

    // ---------------- DISPATCH TEST ----------------

    @Test
    void shouldAssignNearestOnlineDriver() {
        // Driver 1 (closer)
        Driver d1 = new Driver();
        String id1= d1.getId();
        d1.updateLocation(new Location(28.6100, 77.2000));
        d1.setStatus(DriverStatus.ONLINE);
        store.drivers.put(id1, d1);
        // Driver 2 (farther)
        Driver d2 = new Driver();
        String id2= d2.getId();
        d2.updateLocation(new Location(28.6200, 77.2100));
        d2.setStatus(DriverStatus.ONLINE);
        store.drivers.put(id2, d2);
        geoStore.addOrUpdate(d1);
        geoStore.addOrUpdate(d2);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.6110, 77.2010));
        ride.setStatus(RideStatus.REQUESTED);
        // Act
        dispatchService.dispatch(ride);

        // Assert
        assertTrue(ride.getPingedDrivers().contains(id1));
        assertEquals(RideStatus.DRIVER_PINGED, ride.getStatus());
    }

    // ---------------- DRIVER SERVICE TESTS ----------------

    @Test
    void shouldAddDriverAndComputeGeoHash() {
        Driver d = new Driver();
        // 🔧 correct way to set location
        d.updateLocation(new Location(28.61, 77.21));
        String id1= d.getId();
        store.drivers.put(id1, d);
        driverService.add(d);

        Driver stored = store.drivers.get(id1);
        assertNotNull(stored);
        assertNotNull(stored.getGeoHash());
        assertEquals(DriverStatus.ONLINE, stored.getStatus());
    }

    @Test
    void shouldThrowIfLocationMissing() {
        Driver d = new Driver();

        assertThrows(IllegalArgumentException.class,
                () -> driverService.add(d));
    }

    @Test
    void shouldIgnoreOfflineDrivers() {

        GeoDriverStore geoStore = new GeoDriverStore();
        DispatchService dispatchService =
                new DispatchService(geoStore,store, new TimerManager());

        Driver d1 = new Driver();
        d1.updateLocation(new Location(28.61, 77.20));
        d1.setStatus(DriverStatus.OFFLINE);
        store.drivers.put(d1.getId(), d1);
        geoStore.addOrUpdate(d1);

        Ride ride = new Ride();

        ride.setPickup(new Location(28.61, 77.20));

        dispatchService.dispatch(ride);

        assertNull(ride.getAssignedDriverId());
        assertEquals(RideStatus.REQUESTED, ride.getStatus());
    }

    @Test
    void shouldNotPingSameDriverTwice() {

        GeoDriverStore geoStore = new GeoDriverStore();
        TimerManager timerManager = new TimerManager();
        DispatchService dispatchService =
                new DispatchService(geoStore,store, timerManager);

        Driver d1 = new Driver();
        d1.updateLocation(new Location(28.61, 77.20));
        d1.setStatus(DriverStatus.ONLINE);
        store.drivers.put(d1.getId(), d1);
        geoStore.addOrUpdate(d1);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.61, 77.20));

        dispatchService.dispatch(ride);   // first ping
        dispatchService.dispatch(ride);   // retry

        assertEquals(1, ride.getPingedDrivers().size());
    }

    @Test
    void shouldClearTimersWhenRideAccepted() {

        InMemoryStore store = new InMemoryStore();
        DispatchService dispatchService = mock(DispatchService.class);
        TimerManager timerManager = mock(TimerManager.class);

        Driver d1=new Driver();
        store.drivers.put(d1.getId(), d1);
        RideService rideService =
                new RideService(store, dispatchService, timerManager);

        Ride ride = new Ride();
        // 🔧 REQUIRED: valid state before ACCEPT
        ride.setStatus(RideStatus.DRIVER_PINGED);
        ride.getPingedDrivers().add(d1.getId());
        // 🔧 Ride owns multiple timers
        ride.getTimers().add("t1");
        ride.getTimers().add("t2");

        store.rides.put(ride.getId(), ride);

        rideService.accept(ride.getId(), d1.getId());

        // ✅ Correct verification
        verify(timerManager, times(1)).clearTimer("t1");
        verify(timerManager, times(1)).clearTimer("t2");
    }



    @Test
    void shouldHandleNoDriversAvailable() {

        DispatchService dispatchService =
                new DispatchService(new GeoDriverStore(), store, new TimerManager());

        Ride ride = new Ride();
        ride.setPickup(new Location(28.61, 77.20));

        dispatchService.dispatch(ride);

        assertNull(ride.getAssignedDriverId());
        assertEquals(RideStatus.REQUESTED, ride.getStatus());
    }

    @Test
    void allDriversRejectedRideRemainsRequested() {

        GeoDriverStore geoStore = new GeoDriverStore();
        DispatchService dispatchService =
                new DispatchService(geoStore, store, new TimerManager());

        Driver d1 = new Driver();
        d1.updateLocation(new Location(28.61, 77.20));
        d1.setStatus(DriverStatus.ONLINE);

        geoStore.addOrUpdate(d1);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.61, 77.20));

        dispatchService.dispatch(ride);
        d1.recordTimeout();
        dispatchService.dispatch(ride);

        assertEquals(RideStatus.REQUESTED, ride.getStatus());
    }

    @Test
    void shouldFindDriversInNeighborGeohash() {

        GeoDriverStore geoStore = new GeoDriverStore();
        DispatchService dispatchService =
                new DispatchService(geoStore,store, new TimerManager());

        Driver d1 = new Driver();
        String id= d1.getId();
        d1.updateLocation(new Location(28.6200, 77.2100)); // neighbor cell
        d1.setStatus(DriverStatus.ONLINE);
        store.drivers.put(id, d1);
        geoStore.addOrUpdate(d1);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.6100, 77.2000));

        dispatchService.dispatch(ride);

        assertTrue(ride.getPingedDrivers().contains(id));

    }




    @Test
    void shouldStoreTimerIdOnRideWhenDriverPinged() {

        Driver d1 = new Driver();
        d1.updateLocation(new Location(28.61, 77.20));
        d1.setStatus(DriverStatus.ONLINE);

        store.drivers.put(d1.getId(), d1);
        geoStore.addOrUpdate(d1);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.61, 77.20));
        ride.setStatus(RideStatus.REQUESTED);

        dispatchService.dispatch(ride);

        assertFalse(ride.getTimers().isEmpty()); // ➕ NEW
    }

    // ----------------------------------------------------------------
    // RIDE PROGRESSION TESTS (TIME-BASED)
    // ----------------------------------------------------------------

    @Test
    void shouldMoveRideThroughAllStates() throws InterruptedException {

        RideService rideService =
                new RideService(store, dispatchService, timerManager);

        Driver d1 = new Driver();
        d1.updateLocation(new Location(28.61, 77.20));
        d1.setStatus(DriverStatus.ONLINE);

        store.drivers.put(d1.getId(), d1);
        geoStore.addOrUpdate(d1);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.61, 77.20));
        ride.setStatus(RideStatus.REQUESTED);

        store.rides.put(ride.getId(), ride);

        dispatchService.dispatch(ride);

        rideService.accept(ride.getId(), d1.getId());

        Thread.sleep(6000);
        assertEquals(RideStatus.ARRIVING, ride.getStatus());
        Thread.sleep(6000);
        assertEquals(RideStatus.ON_TRIP, ride.getStatus());

        Thread.sleep(11000);
        assertEquals(RideStatus.COMPLETED, ride.getStatus());
    }

    // ----------------------------------------------------------------
    // TIMER CLEANUP TESTS
    // ----------------------------------------------------------------

    @Test
    void cancelShouldClearAllTimers() {

        TimerManager timerManager = mock(TimerManager.class);
        DispatchService dispatchService = mock(DispatchService.class);

        RideService rideService =
                new RideService(store, dispatchService, timerManager);

        Ride ride = new Ride();
        ride.getTimers().add("t1");
        ride.getTimers().add("t2");

        store.rides.put("R1", ride);

        rideService.riderCancel("R1");

        verify(timerManager).clearTimer("t1");
        verify(timerManager).clearTimer("t2");
    }

    @Test
    void noRetryAfterRideAccepted() {

        GeoDriverStore geoStore = new GeoDriverStore();
        TimerManager timerManager = new TimerManager();
        DispatchService dispatchService =
                new DispatchService(geoStore, store, timerManager);

        Driver d1 = new Driver();
        d1.updateLocation(new Location(28.61, 77.20));
        d1.setStatus(DriverStatus.ONLINE);

        store.drivers.put(d1.getId(), d1);
        geoStore.addOrUpdate(d1);

        Ride ride = new Ride();
        ride.setPickup(new Location(28.61, 77.20));
        ride.setStatus(RideStatus.REQUESTED);

        // initial dispatch
        dispatchService.dispatch(ride);

        // 🔧 simulate ACCEPT (correct way)
        ride.setStatus(RideStatus.ACCEPTED);

        // retry dispatch (should do nothing)
        dispatchService.dispatch(ride);

        // ✅ Assertions
        assertEquals(RideStatus.ACCEPTED, ride.getStatus());
        assertTrue(ride.getPingedDrivers().contains(d1.getId()));
    }

}
