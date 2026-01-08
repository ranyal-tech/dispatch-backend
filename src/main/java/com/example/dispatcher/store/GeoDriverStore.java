package com.example.dispatcher.store;

import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.GeoDriver;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GeoDriverStore {

    // 🔑 Source of truth
    private final Map<String, GeoDriver> driversById = new ConcurrentHashMap<>();

    // 🔑 Geo index
    private final Map<String, Set<String>> geoIndex = new ConcurrentHashMap<>();

    public void addOrUpdate(Driver driver) {

        if (driver == null || driver.getId() == null || driver.getLocation() == null) {
            throw new IllegalArgumentException("Invalid driver");
        }

        String driverId = driver.getId();
        String geoHash = driver.getGeoHash();

        driversById.compute(driverId, (id, existing) -> {
            if (existing == null) {
                return new GeoDriver(
                        driverId,
                        driver.getLocation().lat(),
                        driver.getLocation().lng()
                );
            }
            existing.setLatitude(driver.getLocation().lat());
            existing.setLongitude(driver.getLocation().lng());
            return existing;
        });

        // 🔄 Maintain geo index
        geoIndex.values().forEach(set -> set.remove(driverId));
        geoIndex.computeIfAbsent(geoHash, k -> ConcurrentHashMap.newKeySet())
                .add(driverId);
    }

    // 🔍 Lookup by geohash set
    public List<GeoDriver> find(Set<String> geoHashes) {
        return geoHashes.stream()
                .map(geoIndex::get)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .map(driversById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    // ✅ Required for unit tests
    public GeoDriver get(String driverId) {
        return driversById.get(driverId);
    }
}
