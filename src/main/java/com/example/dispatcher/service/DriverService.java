package com.example.dispatcher.service;

import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.DriverStatus;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.store.GeoDriverStore;
import com.example.dispatcher.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class DriverService {

    private final InMemoryStore store;
    private final GeoDriverStore geoStore;

    public DriverService(InMemoryStore store, GeoDriverStore geoStore) {
        this.store = store;
        this.geoStore = geoStore;
    }

    public Driver add(Driver driver) {

        if (driver.getLocation() == null) {
            throw new IllegalArgumentException("Location is required");
        }

        driver.updateLocation(driver.getLocation()); // 🔑 IMPORTANT

        store.drivers.put(driver.getId(), driver);
        geoStore.addOrUpdate(driver);

        return driver;
    }


    public void goOnline(String id) {
        store.drivers.get(id).setStatus(DriverStatus.ONLINE);
    }

    public void goOffline(String id) {
        store.drivers.get(id).setStatus(DriverStatus.OFFLINE);
    }

    public Collection<Driver> getAllDrivers() {
        return store.drivers.values();
    }
}
