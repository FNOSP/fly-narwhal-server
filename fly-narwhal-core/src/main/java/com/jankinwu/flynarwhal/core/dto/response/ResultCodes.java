package com.jankinwu.flynarwhal.core.dto.response;

/** Business result codes shared between server and clients. */
public final class ResultCodes {

    /** Client version is below the server's configured minimum; clients should prompt an upgrade. */
    public static final int CLIENT_VERSION_TOO_LOW = 4001;

    private ResultCodes() {
    }
}
