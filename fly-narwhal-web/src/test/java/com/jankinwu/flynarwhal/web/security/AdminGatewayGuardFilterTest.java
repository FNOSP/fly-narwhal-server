package com.jankinwu.flynarwhal.web.security;

import com.jankinwu.flynarwhal.web.gateway.GatewayToken;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminGatewayGuardFilterTest {

    private final GatewayToken token = new GatewayToken();
    private final AdminGatewayGuardFilter filter = new AdminGatewayGuardFilter(token);

    @Test
    void nonLoopbackGets404() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/codes");
        request.setRemoteAddr("192.168.1.50");
        request.addHeader(GatewayToken.HEADER, token.value());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(404, response.getStatus());
        assertNull(chain.getRequest(), "request must not reach the controller");
    }

    @Test
    void loopbackWithoutTokenGets404() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/codes");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(404, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void loopbackWithWrongTokenGets404() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/codes");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(GatewayToken.HEADER, "forged");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(404, response.getStatus());
    }

    @Test
    void loopbackWithTokenPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/codes");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(GatewayToken.HEADER, token.value());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void nonAdminPathsAlwaysPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/danmu/get");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertNotNull(chain.getRequest());
    }
}