package com.example.dispatcher.controller;

import com.example.dispatcher.model.DriverPingStatusResponse;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.service.RideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
@RestController
@RequestMapping("/rides")
public class RideController {

    private static final Logger log = LoggerFactory.getLogger(RideController.class);

    private final RideService service;

    public RideController(RideService service) {
        this.service = service;
    }

    @PostMapping
    public Ride create(@RequestBody Ride ride) {
        log.info("Creating ride request");
        Ride createdRide = service.create(ride);
        log.info("Ride created with details:", createdRide.getId());
        log.info(
                "Ride created | id={} | status={} | pickup={} | drop={} | driver={}",
                createdRide.getId(),
                createdRide.getStatus(),
                createdRide.getPickup(),
                createdRide.getDrop(),
                createdRide.getAssignedDriverId()
        );
        return createdRide;
    }

    @PostMapping("/{id}/accept")
    public void accept(@PathVariable String id) {
        service.accept(id);
        log.info("Ride accepted id={}", id);
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable String id) {
        log.warn("Cancelling ride id={}", id);
        service.cancel(id);
        log.info("Ride cancelled id={}", id);
    }

    @GetMapping("/{rideId}")
    public Ride getRideStatus(@PathVariable String rideId) {
        log.debug("Fetching ride status for id={}", rideId);
        return service.getRide(rideId);
    }

    @GetMapping
    public Collection<Ride> getAllRides() {
        log.debug("Fetching all rides");
        return service.getAllRides();
    }

    @GetMapping("/{rideId}/drivers/{driverId}/ping-status")
    public DriverPingStatusResponse getDriverPingStatus(
            @PathVariable String rideId,
            @PathVariable String driverId) {

        return service.getPingStatus(rideId, driverId);
    }

}
