package com.example.dispatcher.controller;

import com.example.dispatcher.model.ApiResponse;
import com.example.dispatcher.model.DriverPingStatusResponse;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.service.RideService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/rides")
public class RideController {

    private static final Logger log = LoggerFactory.getLogger(RideController.class);

    private final RideService service;

    public RideController(RideService service) {
        this.service = service;
    }

    // Create Ride
    @PostMapping
    public ResponseEntity<ApiResponse<Ride>> create( @Valid @RequestBody Ride ride) {
        log.info("Creating ride request");
        Ride createdRide = service.create(ride);

        log.info("Ride created with id={}", createdRide.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Ride created successfully", createdRide));
    }

    // Accept Ride by Driver
    @PostMapping("/{rideId}/accept/driver/{driverId}")
    public ResponseEntity<ApiResponse<DriverPingStatusResponse>> accept(
            @PathVariable String rideId,
            @PathVariable String driverId) {

        log.info("Driver {} accepting ride id={}", driverId, rideId);

        DriverPingStatusResponse response =
                service.accept(rideId, driverId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(true, "Ride accepted", response));
    }


    // Rider Cancel
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<String>> riderCancel(@PathVariable String id) {
        log.warn("Rider cancelling ride id={}", id);
        String res = service.riderCancel(id);

        return ResponseEntity.ok(
                new ApiResponse<>(true, res, res)
        );
    }

    // Driver Cancel
    @PostMapping("/{id}/cancel/driver/{driverId}")
    public ResponseEntity<ApiResponse<String>> driverCancel(
            @PathVariable String id,
            @PathVariable String driverId) {

        log.warn("Driver {} cancelling ride id={}", driverId, id);
        String res = service.driverCancel(id, driverId);

        return ResponseEntity.ok(
                new ApiResponse<>(true, res, res)
        );
    }

    // Get Single Ride
    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<Ride>> getRide(@PathVariable String rideId) {
        log.debug("Fetching ride id={}", rideId);
        Ride ride = service.getRide(rideId);

        return ResponseEntity
                .ok(new ApiResponse<>(true, "Ride fetched", ride));
    }

    // Get All Rides
    @GetMapping
    public ResponseEntity<ApiResponse<Collection<Ride>>> getAllRides() {
        log.debug("Fetching all rides");
        Collection<Ride> rides = service.getAllRides();

        return ResponseEntity
                .ok(new ApiResponse<>(true, "All rides fetched", rides));
    }

    // Get Driver Ping Status
    @GetMapping("/{rideId}/drivers/{driverId}/ping-status")
    public ResponseEntity<ApiResponse<DriverPingStatusResponse>> getDriverPingStatus(
            @PathVariable String rideId,
            @PathVariable String driverId) {

        DriverPingStatusResponse response = service.getPingStatus(rideId, driverId);

        return ResponseEntity
                .ok(new ApiResponse<>(true, "Ping status fetched", response));
    }
}
