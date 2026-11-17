package com.jankinwu.flynarwhal.web.security;

/**
 * Request attribute carrying the authenticated {@link com.jankinwu.flynarwhal.web.entity.ClientAuthCode}
 * from the Fnauth interceptor to downstream advice/controllers.
 */
public final class ClientCodeAttributes {

    public static final String CLIENT_CODE = "fnClientCode";

    private ClientCodeAttributes() {
    }
}