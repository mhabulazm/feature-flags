package com.acme.flags.spi;

public final class FlagEngineUnavailableException extends RuntimeException {

    public FlagEngineUnavailableException(String message) {
        super(message);
    }

    public FlagEngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
