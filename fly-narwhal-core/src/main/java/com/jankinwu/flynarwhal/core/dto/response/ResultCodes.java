package com.jankinwu.flynarwhal.core.dto.response;

/** Business result codes shared between server and clients. */
public final class ResultCodes {

    /** Client version is below the server's configured minimum; clients should prompt an upgrade. */
    public static final int CLIENT_VERSION_TOO_LOW = 4001;

    /** Per-client code: keyid unknown, malformed, or the HMAC does not match (one opaque answer on purpose). */
    public static final int CODE_INVALID = 4101;

    /** Per-client code exists but is past its expiry. */
    public static final int CODE_EXPIRED = 4102;

    /** Per-client code was disabled by the administrator. */
    public static final int CODE_DISABLED = 4103;

    /** Per-client code is bound to a different instance; the binding cannot be moved, issue a new code. */
    public static final int CODE_DEVICE_MISMATCH = 4104;

    /** Too many failed auth attempts from this IP; retry after the cooldown. */
    public static final int CODE_RATE_LIMITED = 4105;

    /** Nonce reused or timestamp outside the acceptance window. */
    public static final int CODE_REPLAYED = 4106;

    /** A read-only code called an endpoint that edits server-side configuration. */
    public static final int CODE_READ_ONLY = 4107;

    private ResultCodes() {
    }
}
