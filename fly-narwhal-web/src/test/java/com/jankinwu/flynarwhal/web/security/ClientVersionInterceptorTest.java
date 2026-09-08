package com.jankinwu.flynarwhal.web.security;

import com.jankinwu.flynarwhal.web.config.ClientVersionProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ClientVersionInterceptorTest {

    private ClientVersionProperties properties;
    private ClientVersionInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        properties = new ClientVersionProperties();
        properties.setMinVersion("2.3.2");
        interceptor = new ClientVersionInterceptor(properties, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI("/api/analysis/status");
    }

    @Test
    void parseVersionHandlesSuffixes() {
        assertArrayEquals(new int[]{2, 3, 2}, ClientVersionInterceptor.parseVersion("2.3.2"));
        assertArrayEquals(new int[]{2, 4, 0}, ClientVersionInterceptor.parseVersion("2.4.0-beta.1"));
        assertArrayEquals(new int[]{2, 4, 0}, ClientVersionInterceptor.parseVersion(" 2.4.0+build5 "));
        assertArrayEquals(new int[]{}, ClientVersionInterceptor.parseVersion(null));
        assertArrayEquals(new int[]{}, ClientVersionInterceptor.parseVersion("  "));
    }

    @Test
    void compareVersions() {
        assertTrue(ClientVersionInterceptor.compare(new int[]{2, 4, 0}, new int[]{2, 3, 2}) > 0);
        assertTrue(ClientVersionInterceptor.compare(new int[]{2, 3}, new int[]{2, 3, 2}) < 0);
        assertEquals(0, ClientVersionInterceptor.compare(new int[]{2, 3, 2}, new int[]{2, 3, 2, 0}));
        assertTrue(ClientVersionInterceptor.compare(new int[]{10, 0}, new int[]{9, 9}) > 0);
    }

    @Test
    void rejectsClientBelowMinimum() throws Exception {
        request.addHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER, "2.3.1");
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("4001"), body);
        assertTrue(body.contains("当前服务端所需最低客户端版本： 2.3.2，请升级客户端"), body);
    }

    @Test
    void allowsClientAtOrAboveMinimum() throws Exception {
        request.addHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER, "2.3.2");
        assertTrue(interceptor.preHandle(request, response, new Object()));

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/analysis/status");
        request.addHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER, "2.4.0-beta");
        response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void allowsLegacyClientsWithoutVersionHeader() throws Exception {
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void allowsUnparseableVersionHeader() throws Exception {
        request.addHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER, "nonsense");
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void exemptsConfigEndpoints() throws Exception {
        request.setRequestURI("/api/config/version");
        request.addHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER, "1.0.0");
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void allowsEverythingWhenGateDisabled() throws Exception {
        properties.setMinVersion("");
        request.addHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER, "0.0.1");
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }
}
