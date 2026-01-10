package com.example.dispatcher.controller;

import com.example.dispatcher.model.ApiResponse;
import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.DriverPingStatusResponse;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.service.DriverRideService;
import com.example.dispatcher.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/drivers")
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    private final DriverService service;
    private final DriverRideService driverRideService;

    public DriverController(DriverService service, DriverRideService driverRideService) {
        this.service = service;
        this.driverRideService = driverRideService;
    }

    // ADD DRIVER
    @PostMapping
    public ResponseEntity<ApiResponse<Driver>> add(@RequestBody Driver driver) {
        log.info("Adding new driver");
        Driver createdDriver = service.add(driver);
        log.info("Driver added id={}", createdDriver.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Driver added successfully", createdDriver));
    }

    // Change the status online
    @PatchMapping("/{id}/online")
    public ResponseEntity<ApiResponse<Void>> online(@PathVariable String id) {
        log.info("Setting driver ONLINE id={}", id);
        service.goOnline(id);

        return ResponseEntity
                .ok(new ApiResponse<>(true, "Driver is now online", null));
    }

    // change the status offline
    @PatchMapping("/{id}/offline")
    public ResponseEntity<ApiResponse<Void>> offline(@PathVariable String id) {
        log.info("Setting driver OFFLINE id={}", id);
        service.goOffline(id);

        return ResponseEntity
                .ok(new ApiResponse<>(true, "Driver is now offline", null));
    }

    // Get all drivers
    @GetMapping
    public ResponseEntity<ApiResponse<Collection<Driver>>> getAllDrivers() {
        log.debug("Fetching all drivers");
        Collection<Driver> drivers = service.getAllDrivers();

        return ResponseEntity
                .ok(new ApiResponse<>(true, "Drivers fetched successfully", drivers));
    }

    // Get all rides for a driver
    @GetMapping("/{driverId}/rides")
    public ResponseEntity<ApiResponse<List<DriverPingStatusResponse>>> getAllRidesForDriver(
            @PathVariable String driverId) {

        log.debug("Fetching rides for driver {}", driverId);

        List<DriverPingStatusResponse> rides =
                driverRideService.getAllRidesForDriver(driverId);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Driver rides fetched successfully", rides)
        );
    }
}
