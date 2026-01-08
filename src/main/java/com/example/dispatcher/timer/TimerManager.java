package com.example.dispatcher.timer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.UUID;
@Component
public class TimerManager {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);

    // 🔑 timers tracked by rideId
    private final Map<String, ScheduledFuture<?>> timers =
            new ConcurrentHashMap<>();

    /**
     * Schedule a timeout for a ride.
     * Any existing timer for the ride is cancelled.
     */
    public String schedule(
            String rideId,
            String type,
            long delaySeconds,
            Runnable task
    ) {

        // 🔧 CHANGE: unique timerId
        String timerId = rideId + ":" + type + ":" + UUID.randomUUID();

        ScheduledFuture<?> future =
                scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);

        timers.put(timerId, future);

        return timerId;
    }

    /**
     * Cancel a single timer
     */
    public void clearTimer(String timerId) {

        ScheduledFuture<?> future = timers.remove(timerId);

        if (future != null) {
            future.cancel(true);
        }
    }

}
