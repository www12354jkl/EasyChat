package com.faust.easychat.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.faust.easychat.exception.GlobalExceptionHandler;
import com.faust.easychat.filter.JwtAuthFilter;
import com.faust.easychat.mapper.AuthMapper;
import com.faust.easychat.mapper.UserSettingsMapper;
import com.faust.easychat.service.impl.AuthServiceImpl;
import com.faust.easychat.service.impl.TokenBlacklistServiceImpl;
import com.faust.easychat.util.EmailUtil;
import com.faust.easychat.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutTest {

    private static final String SECRET = "12345678901234567890123456789012";
    private static final String PUBLIC_PATHS = "/api/auth/login,/api/auth/register,/api/auth/token/refresh";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private JwtUtil jwtUtil;
    private Map<String, String> redisStore;
    private AtomicBoolean failRedisWrites;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        redisStore = new ConcurrentHashMap<>();
        failRedisWrites = new AtomicBoolean(false);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.hasKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return redisStore.containsKey(key);
        });
        lenient().when(valueOps.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return redisStore.get(key);
        });
        lenient().doAnswer(inv -> {
            if (failRedisWrites.get()) {
                throw new DataAccessResourceFailureException("redis write failed");
            }
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            redisStore.put(key, val);
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 1_800_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604_800_000L);

        TokenBlacklistServiceImpl tokenBlacklistService = new TokenBlacklistServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(tokenBlacklistService, "refreshExpirationMs", 604_800_000L);

        AuthServiceImpl authService = new AuthServiceImpl(
                authMapper,
                userSettingsMapper,
                jwtUtil,
                emailUtil,
                redisTemplate,
                tokenBlacklistService,
                passwordEncoder);
        AuthController authController = new AuthController(authService);
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtUtil, tokenBlacklistService, PUBLIC_PATHS);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(jwtAuthFilter)
                .build();
    }

    @Test
    void logout_blacklistsBothTokensAndBlocksSubsequentAccessAndRefresh() throws Exception {
        String accessToken = jwtUtil.generateAccessToken("user-1");
        String refreshToken = jwtUtil.generateRefreshToken("user-1");
        String body = "{\"refreshToken\":\"  \\\"" + refreshToken + "\\\"  \"}";

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer   " + accessToken + "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertTrue(redisStore.containsKey("token:blacklist:" + accessToken));
        assertTrue(redisStore.containsKey("token:blacklist:" + refreshToken));

        // Reusing the logged-out access token (even with extra whitespace) must be rejected by JwtAuthFilter
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer   " + accessToken + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        // Reusing the logged-out refresh token must be rejected by /api/auth/token/refresh
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"" + refreshToken + "\""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1008));
    }

    @Test
    void logout_whenRedisWriteFails_doesNotReturnSuccess() throws Exception {
        String accessToken = jwtUtil.generateAccessToken("user-1");
        failRedisWrites.set(true);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void logout_withoutAuthorizationHeader_isRejectedByJwtAuthFilter() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
