package io.mehdieidi.modless.platform.core;

public class PlatformException extends RuntimeException {

    private final int status;

    public PlatformException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
