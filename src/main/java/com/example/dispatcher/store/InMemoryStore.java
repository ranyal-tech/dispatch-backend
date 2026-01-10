package com.example.dispatcher.store;

import com.example.dispatcher.model.Driver;
import com.example.dispatcher.model.Ride;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStore {
    public Map<String, Driver> drivers = new ConcurrentHashMap<>();
    public Map<String, Ride> rides = new ConcurrentHashMap<>();
    public final Map<String, Boolean> rideTimerExpired = new ConcurrentHashMap<>();
}

