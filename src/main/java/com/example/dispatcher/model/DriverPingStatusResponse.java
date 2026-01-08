package com.example.dispatcher.model;

    public class DriverPingStatusResponse {
        private String rideId;
        private String driverId;
        private boolean pinged;
        private boolean currentlyAssigned;
        private RideStatus rideStatus;

        public String getRideId() {
            return rideId;
        }

        public void setRideId(String rideId) {
            this.rideId = rideId;
        }

        public String getDriverId() {
            return driverId;
        }

        public void setDriverId(String driverId) {
            this.driverId = driverId;
        }

        public boolean isPinged() {
            return pinged;
        }

        public void setPinged(boolean pinged) {
            this.pinged = pinged;
        }

        public boolean isCurrentlyAssigned() {
            return currentlyAssigned;
        }

        public void setCurrentlyAssigned(boolean currentlyAssigned) {
            this.currentlyAssigned = currentlyAssigned;
        }

        public RideStatus getRideStatus() {
            return rideStatus;
        }

        public void setRideStatus(RideStatus rideStatus) {
            this.rideStatus = rideStatus;
        }
    }

