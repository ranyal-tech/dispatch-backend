package com.example.dispatcher.state;

import com.example.dispatcher.model.RideStatus;

import java.util.Map;
import java.util.Set;

public class RideStateMachine {

    private static final Map<RideStatus, Set<RideStatus>> transitions = Map.of(
            RideStatus.REQUESTED, Set.of(RideStatus.DRIVER_PINGED, RideStatus.CANCELLED),
            RideStatus.DRIVER_PINGED, Set.of(RideStatus.ACCEPTED, RideStatus.REQUESTED),
            RideStatus.ACCEPTED, Set.of(RideStatus.ARRIVING,RideStatus.REQUESTED, RideStatus.CANCELLED),
            RideStatus.ARRIVING, Set.of(RideStatus.ON_TRIP,RideStatus.CANCELLED),
            RideStatus.ON_TRIP, Set.of(RideStatus.COMPLETED,RideStatus.CANCELLED)

    );

    public static void validate(RideStatus from, RideStatus to) {
        if (!transitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Invalid transition");
        }
    }
}

