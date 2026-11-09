package com.example.vornx.common.exception;

/** Throw when something does not exist, e.g. "Permit 42 not found". Becomes HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
