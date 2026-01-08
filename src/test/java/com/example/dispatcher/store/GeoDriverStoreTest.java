package com.example.dispatcher.store;

import com.example.dispatcher.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeoDriverStoreTest {

    private GeoDriverStore geoStore;
    @BeforeEach
    void init() {
        geoStore = new GeoDriverStore();
    }

    @Test
    void shouldAddOrUpdateDriverLocation() {
        Driver d = new Driver();
        String id=d.getId();
        d.updateLocation(new Location(28.61, 77.21));
        d.updateLocation(d.getLocation());

        geoStore.addOrUpdate(d);

        GeoDriver geo = geoStore.get(id);
        assertNotNull(geo);
        assertEquals(28.61, geo.getLatitude());
        assertEquals(77.21, geo.getLongitude());
    }



    @Test
    void shouldUpdateExistingDriverLocation() {
        Driver d = new Driver();
        d.updateLocation(new Location(28.61, 77.21));
        geoStore.addOrUpdate(d);

        d.updateLocation(new Location(28.62, 77.22));
        geoStore.addOrUpdate(d);

        GeoDriver geo = geoStore.get(d.getId());
        assertEquals(28.62, geo.getLatitude());
        assertEquals(77.22, geo.getLongitude());
    }

}
