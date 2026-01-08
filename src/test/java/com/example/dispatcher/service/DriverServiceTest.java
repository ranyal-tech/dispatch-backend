package com.example.dispatcher.service;

import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.DriverStatus;
import com.example.dispatcher.model.Location;
import com.example.dispatcher.store.GeoDriverStore;
import com.example.dispatcher.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    private InMemoryStore store;
    private GeoDriverStore geoStore;
    private DriverService driverService;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        geoStore = new GeoDriverStore();
        driverService = new DriverService(store, geoStore);
    }

    @Test
    void shouldAddDriverAndComputeGeoHash() {
        Driver d = new Driver();
        d.updateLocation(new Location(28.61, 77.21));

        driverService.add(d);

        Driver stored = store.drivers.get("D1");
        assertNotNull(stored);
        assertNotNull(stored.getGeoHash());
        assertEquals(DriverStatus.OFFLINE, stored.getStatus());
    }

    @Test
    void shouldThrowIfLocationMissing() {
        Driver d = new Driver();
        assertThrows(IllegalArgumentException.class,
                () -> driverService.add(d));
    }
}
