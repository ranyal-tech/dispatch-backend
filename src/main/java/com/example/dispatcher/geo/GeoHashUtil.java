package com.example.dispatcher.geo;

import ch.hsr.geohash.GeoHash;

import java.util.HashSet;
import java.util.Set;

public class GeoHashUtil {

    private static final int PRECISION = 6;

    public static String encode(double lat, double lng) {
        return GeoHash.withCharacterPrecision(lat, lng, PRECISION)
                .toBase32();
    }

    /**
     * Returns 8 neighboring geohashes
     */
//    public static Set<String> neighbors(String geoHash) {
//
//        GeoHash center = GeoHash.fromGeohashString(geoHash);
//        Set<String> neighbors = new HashSet<>();
//
//        // getAdjacent() RETURNS GeoHash[] IN YOUR VERSION
//        GeoHash[] adjacent = center.getAdjacent();
//
//        for (GeoHash gh : adjacent) {
//            neighbors.add(gh.toBase32());
//        }
//
//        return neighbors;
//    }

    public static Set<String> neighbors(String geoHash, int ring) {

        if (geoHash == null || geoHash.isEmpty()) {
            throw new IllegalArgumentException("geoHash cannot be null or empty");
        }
        if (ring < 0) {
            throw new IllegalArgumentException("ring must be >= 0");
        }

        Set<String> result = new HashSet<>();
        Set<String> frontier = new HashSet<>();

        // ring 0
        result.add(geoHash);
        frontier.add(geoHash);

        // expand rings
        for (int i = 0; i < ring; i++) {
            Set<String> next = new HashSet<>();

            for (String h : frontier) {
                GeoHash center = GeoHash.fromGeohashString(h);
                GeoHash[] adjacents = center.getAdjacent();

                for (GeoHash gh : adjacents) {
                    String adj = gh.toBase32();
                    if (result.add(adj)) {
                        next.add(adj);
                    }
                }
            }
            frontier = next;
        }

        return result;
    }

    /**
     * Convenience method:
     * center + immediate neighbors (ring = 1)
     */
    public static Set<String> neighbors(String geoHash) {
        return neighbors(geoHash, 1);
    }

    public static double distanceMeters(
            double lat1, double lon1,
            double lat2, double lon2) {

        final int EARTH_RADIUS = 6371000; // meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

}
