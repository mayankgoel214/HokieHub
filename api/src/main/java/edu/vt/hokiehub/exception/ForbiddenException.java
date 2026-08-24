package edu.vt.hokiehub.exception;

/** Thrown when the caller is authenticated but does not own the resource. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
