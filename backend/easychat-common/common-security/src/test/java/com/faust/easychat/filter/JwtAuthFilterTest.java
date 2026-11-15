package com.faust.easychat.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.faust.easychat.context.UserContext;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.JwtUtil;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRET = "12345678901234567890123456789012";
    private static final String PUBLIC_PATHS = "/api/auth/login,/api/auth/register,/swagger-ui,/v3/api-docs";

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private JwtUtil jwtUtil;
    private JwtAuthFilter filter;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 1_800_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604_800_000L);
        filter = new JwtAuthFilter(jwtUtil, tokenBlacklistService, PUBLIC_PATHS);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void validAccessToken_passesAndClearsUserContext() throws Exception {
        String token = jwtUtil.generateAccessToken("user-1");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(tokenBlacklistService.isRevokedByPasswordChange(eq("user-1"), any())).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<String> seenUserId = new AtomicReference<>();

        filter.doFilter(req, resp, (request, response) -> seenUserId.set(UserContext.get()));

        assertEquals("user-1", seenUserId.get());
        assertNull(UserContext.get());
        assertEquals(200, resp.getStatus());
        assertEquals("", resp.getContentAsString());
    }

    @Test
    void validAccessTokenWithBearerWhitespace_extractsTrimmedTokenAndPasses() throws Exception {
        String token = jwtUtil.generateAccessToken("user-2");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(tokenBlacklistService.isRevokedByPasswordChange(eq("user-2"), any())).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer   " + token + "   ");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(req, resp, (request, response) -> passed.set(true));

        assertTrue(passed.get());
        verify(tokenBlacklistService).isBlacklisted(token);
    }

    @Test
    void blacklistedToken_isRejectedWith401JsonAndChainNotCalled() throws Exception {
        String token = jwtUtil.generateAccessToken("user-1");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(req, resp, (request, response) -> passed.set(true));

        assertFalse(passed.get());
        assertEquals(200, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":401"));
    }

    @Test
    void blacklistedTokenWithBearerWhitespace_isRejectedWith401Json() throws Exception {
        String token = jwtUtil.generateAccessToken("user-1");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer   " + token + "  ");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(req, resp, (request, response) -> passed.set(true));

        assertFalse(passed.get());
        assertEquals(200, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":401"));
        verify(tokenBlacklistService).isBlacklisted(token);
    }

    @Test
    void missingNonBearerOrBlankBearerHeader_isRejectedWithoutCallingRedis() throws Exception {
        for (String header : new String[] { null, "", "Basic abc", "Bearer", "Bearer   " }) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
            if (header != null) {
                req.addHeader("Authorization", header);
            }
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean passed = new AtomicBoolean();

            filter.doFilter(req, resp, (request, response) -> passed.set(true));

            assertFalse(passed.get());
            assertEquals(200, resp.getStatus());
            assertTrue(resp.getContentAsString().contains("\"code\":401"));
            assertTrue(resp.getContentAsString().contains("未登录"));
        }
        verifyNoInteractions(tokenBlacklistService);
    }

    @Test
    void refreshTokenAsBearer_isRejectedWith401Json() throws Exception {
        String refresh = jwtUtil.generateRefreshToken("user-1");
        when(tokenBlacklistService.isBlacklisted(refresh)).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer " + refresh);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(req, resp, (request, response) -> passed.set(true));

        assertFalse(passed.get());
        assertEquals(200, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":401"));
        assertTrue(resp.getContentAsString().contains("无效token"));
    }

    @Test
    void expiredAndMalformedTokens_areRejectedWith401Json() throws Exception {
        String expired = Jwts.builder()
                .subject("user-1")
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);

        for (String badToken : new String[] { expired, "not.a.jwt" }) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
            req.addHeader("Authorization", "Bearer " + badToken);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean passed = new AtomicBoolean();

            filter.doFilter(req, resp, (request, response) -> passed.set(true));

            assertFalse(passed.get());
            assertEquals(200, resp.getStatus());
            assertTrue(resp.getContentAsString().contains("\"code\":401"));
        }
    }

    @Test
    void passwordChangeRevokedToken_isRejectedWith401Json() throws Exception {
        String token = jwtUtil.generateAccessToken("user-1");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(tokenBlacklistService.isRevokedByPasswordChange(eq("user-1"), any())).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(req, resp, (request, response) -> passed.set(true));

        assertFalse(passed.get());
        assertEquals(200, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":401"));
    }

    @Test
    void redisDataAccessException_returnsHttp503() throws Exception {
        String token = jwtUtil.generateAccessToken("user-1");
        when(tokenBlacklistService.isBlacklisted(token))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(req, resp, (request, response) -> passed.set(true));

        assertFalse(passed.get());
        assertEquals(503, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":503"));
    }

    @Test
    void downstreamFilterException_propagatesAndClearsUserContext() {
        String token = jwtUtil.generateAccessToken("user-1");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(tokenBlacklistService.isRevokedByPasswordChange(eq("user-1"), any())).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain throwingChain = (request, response) -> {
            throw new ServletException("downstream failure");
        };

        assertThrows(ServletException.class, () -> filter.doFilter(req, resp, throwingChain));
        assertNull(UserContext.get());
    }

    @Test
    void publicPaths_ignoresBlankEntriesAndAllowsExactOrSubpathOnly() throws Exception {
        JwtAuthFilter sparseFilter = new JwtAuthFilter(
                jwtUtil,
                tokenBlacklistService,
                " , /api/auth/login , , /swagger-ui , ");

        MockHttpServletRequest loginReq = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse loginResp = new MockHttpServletResponse();
        AtomicBoolean loginPassed = new AtomicBoolean();
        sparseFilter.doFilter(loginReq, loginResp, (r, s) -> loginPassed.set(true));
        assertTrue(loginPassed.get());

        MockHttpServletRequest swaggerReq = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse swaggerResp = new MockHttpServletResponse();
        AtomicBoolean swaggerPassed = new AtomicBoolean();
        sparseFilter.doFilter(swaggerReq, swaggerResp, (r, s) -> swaggerPassed.set(true));
        assertTrue(swaggerPassed.get());

        for (String protectedPath : new String[] {
                "/api/user/profile",
                "/api/auth/logout",
                "/api/auth/login-extra",
                "/api/auth/login/../logout",
                "/api/auth/login/%2e%2e/logout",
                "/api/auth/login/%2f../logout",
                "/api/auth/login;bypass=1"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", protectedPath);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean passed = new AtomicBoolean();
            sparseFilter.doFilter(req, resp, (r, s) -> passed.set(true));
            assertFalse(passed.get(), "Path should require auth: " + protectedPath);
            assertEquals(200, resp.getStatus());
            assertTrue(resp.getContentAsString().contains("\"code\":401"));
        }
        verify(tokenBlacklistService, never()).isBlacklisted(anyString());
    }

    @Test
    void publicPaths_emptyOrAllBlankOrNull_neverBypassesAuth() throws Exception {
        for (String publicPaths : new String[] { "", " , , ", null }) {
            JwtAuthFilter strictFilter = new JwtAuthFilter(jwtUtil, tokenBlacklistService, publicPaths);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean passed = new AtomicBoolean();

            strictFilter.doFilter(req, resp, (r, s) -> passed.set(true));

            assertFalse(passed.get());
            assertEquals(200, resp.getStatus());
            assertTrue(resp.getContentAsString().contains("\"code\":401"));
        }
    }

    @Test
    void multipleAuthorizationHeaders_isRejectedWith401WithoutCallingRedis() throws Exception {
        String token = jwtUtil.generateAccessToken("user-1");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/profile");
        req.addHeader("Authorization", "Bearer " + token);
        req.addHeader("Authorization", "Bearer other-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(req, resp, (r, s) -> passed.set(true));

        assertFalse(passed.get());
        assertEquals(200, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":401"));
        verifyNoInteractions(tokenBlacklistService);
    }
}
