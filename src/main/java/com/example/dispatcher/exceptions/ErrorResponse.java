package com.example.dispatcher.exceptions;

public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private long timestamp;

    public static ErrorResponse of(
            int status,
            String error,
            String message
    ) {
        ErrorResponse r = new ErrorResponse();
        r.status = status;
        r.error = error;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    // getters
}

