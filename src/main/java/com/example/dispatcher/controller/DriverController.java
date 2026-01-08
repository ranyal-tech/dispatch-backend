package com.example.dispatcher.controller;

import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.Ride;
import com.example.dispatcher.service.DriverService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
@RestController
@RequestMapping("/drivers")
public class DriverController {

    private final DriverService service;

    public DriverController(DriverService service) {
        this.service = service;
    }

    @PostMapping
    public Driver add(@RequestBody Driver d) {
         return service.add(d);
    }

    @PatchMapping("/{id}/online")
    public void online(@PathVariable String id) {
        service.goOnline(id);
    }

    @PatchMapping("/{id}/offline")
    public void offline(@PathVariable String id) {
        service.goOffline(id);
    }

    @GetMapping
    public Collection<Driver> getAllDrivers() {
        return service.getAllDrivers();
    }
}
