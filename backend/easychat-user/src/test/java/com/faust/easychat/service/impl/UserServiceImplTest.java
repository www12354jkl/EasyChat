package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.faust.easychat.dto.user.ResetPasswordRequest;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.entity.User;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.UserMapper;
import com.faust.easychat.mapper.UserSettingsMapper;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.CosUtil;
import com.faust.easychat.util.EmailUtil;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;
    @Mock
    private CosUtil cosUtil;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void batchGetUsers_blankIds_returnsEmpty() {
        assertTrue(userService.batchGetUsers("").isEmpty());
        assertTrue(userService.batchGetUsers("  ,  ").isEmpty());
    }

    @Test
    void batchGetUsers_validIds_returnsUsersInOrder() {
        User u1 = user("id-1", "Alice", "a.png");
        User u2 = user("id-2", "Bob", "b.png");
        when(userMapper.selectByIds(eq(List.of("id-2", "id-1")))).thenReturn(List.of(u1, u2));

        List<UserBriefDTO> result = userService.batchGetUsers("id-2, id-1");

        assertEquals(2, result.size());
        assertEquals("id-2", result.get(0).getUserId());
        assertEquals("Bob", result.get(0).getNickname());
        assertEquals("id-1", result.get(1).getUserId());
        assertEquals("Alice", result.get(1).getNickname());
        verify(userMapper).selectByIds(eq(List.of("id-2", "id-1")));
    }

    @Test
    void batchGetUsers_missingUser_throws() {
        when(userMapper.selectByIds(eq(List.of("id-1")))).thenReturn(List.of());

        assertThrows(AppException.class, () -> userService.batchGetUsers("id-1"));
    }

    @Test
    void resetPassword_revokesTokensIssuedBeforeTheChange() {
        User user = user("id-1", "Alice", "a.png");
        user.setEmail("a@b.c");
        user.setPassword("old-hash");
        when(userMapper.selectById("id-1")).thenReturn(user);
        when(emailUtil.verifyCode("a@b.c", "123456")).thenReturn(true);
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("a@b.c");
        request.setCode("123456");
        request.setOldPassword("old-pass");
        request.setNewPassword("new-pass");

        userService.resetPassword("id-1", request);

        verify(tokenBlacklistService).revokeUserTokens("id-1");
    }

    private static User user(String id, String nickname, String avatar) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatar(avatar);
        return user;
    }
}
