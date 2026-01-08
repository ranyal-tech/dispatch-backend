package com.example.dispatcher.model;

public class GeoDriver {

    private final String driverId;
    private double latitude;
    private double longitude;

    public GeoDriver(String driverId, double latitude, double longitude) {
        if (driverId == null) {
            throw new IllegalArgumentException("driverId cannot be null");
        }
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getDriverId() {
        return driverId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
