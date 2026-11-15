package com.faust.easychat.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.faust.easychat.dto.user.ProfileDTO;
import com.faust.easychat.dto.user.ResetPasswordRequest;
import com.faust.easychat.dto.user.SettingsRequest;
import com.faust.easychat.dto.user.UpdateProfileRequest;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.dto.user.UserInfoDTO;
import com.faust.easychat.entity.User;
import com.faust.easychat.entity.UserSettings;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.UserMapper;
import com.faust.easychat.mapper.UserSettingsMapper;
import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.service.UserService;
import com.faust.easychat.util.CosUtil;
import com.faust.easychat.util.EmailUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final UserSettingsMapper userSettingsMapper;

    private final CosUtil cosUtil;

    private final EmailUtil emailUtil;

    private final BCryptPasswordEncoder passwordEncoder;

    private final TokenBlacklistService tokenBlacklistService;

    private final StringRedisTemplate redisTemplate;


    @Override
    public ProfileDTO getProfileById(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw AppException.userNotFound();
        }
        ProfileDTO dto = new ProfileDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }

    @Override
    public void updateProfileById(String id, UpdateProfileRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw AppException.userNotFound();
        }
        user.setNickname(request.getNickname());
        user.setGender(request.getGender());
        user.setAvatar(request.getAvatar());
        user.setRegion(request.getRegion());
        user.setRegionCode(request.getRegionCode());
        user.setSignature(request.getSignature());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public String uploadAvatar(String id, MultipartFile file) {
        if (file.isEmpty()) {
            throw AppException.badRequest("请选择文件");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = null;
        if (originalFilename != null) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        if (suffix == null || !Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(suffix.toLowerCase())) {
            throw AppException.badRequest("只支持 jpg/jpeg/png/gif/webp 格式");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            throw AppException.badRequest("文件大小不能超过2MB");
        }

        User user = userMapper.selectById(id);
        if (user == null) {
            throw AppException.userNotFound();
        }

        String path = "easychat/user-avatars/" + UUID.randomUUID() + suffix;
        String url = cosUtil.upload(file, path);

        try {
            user.setAvatar(url);
            userMapper.updateById(user);
        } catch (Exception e) {
            log.error("头像URL入库失败，COS文件成为孤儿: path={}, error={}", path, e.getMessage());
            throw AppException.internalError("头像上传失败");
        }

        return url;
    }

    @Override
    public void resetPassword(String id, ResetPasswordRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw AppException.userNotFound();
        }
        // 校验请求邮箱与当前登录账号一致，防止用他人邮箱发码验证
        if (!request.getEmail().equals(user.getEmail())) {
            throw AppException.badRequest("邮箱与当前账号不匹配");
        }
        if (!emailUtil.verifyCode(request.getEmail(), request.getCode())) {
            throw AppException.verifyCodeError();
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw AppException.passwordError();
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        tokenBlacklistService.revokeUserTokens(id);
    }

    @Override
    public void sendResetCode(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw AppException.userNotFound();
        }
        // 60s 发送间隔防刷，与 auth 发码共用同一把锁（同一邮箱共用一个验证码 key）
        String limitKey = "email:code:limit:" + user.getEmail();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw AppException.tooManyRequests("发送过于频繁，请稍后再试");
        }
        String code = emailUtil.generateCode();
        emailUtil.sendVerifyCode(user.getEmail(), code);
        redisTemplate.opsForValue().set(limitKey, "1", 60, TimeUnit.SECONDS);
    }

    @Override
    public void updateFriendVerify(String userId, SettingsRequest request) {
        if (userMapper.selectById(userId) == null) {
            throw AppException.userNotFound();
        }
        UserSettings userSettings = userSettingsMapper.selectByUserId(userId);
        if (userSettings == null) {
            // 旧用户（注册创建逻辑之前注册的）无 settings 行：插入默认
            userSettings = new UserSettings();
            userSettings.setUserId(userId);
            userSettings.setFriendVerify(request.getFriendVerify());
            userSettingsMapper.insert(userSettings);
        } else {
            userSettings.setFriendVerify(request.getFriendVerify());
            userSettingsMapper.updateById(userSettings);
        }
    }

    @Override
    public SettingsRequest getSettings(String userId) {
        if (userMapper.selectById(userId) == null) {
            throw AppException.userNotFound();
        }
        SettingsRequest dto = new SettingsRequest();
        UserSettings userSettings = userSettingsMapper.selectByUserId(userId);
        // 只读接口不产生写副作用：查无返回默认值 1（需要验证），懒创建交给写路径 updateFriendVerify，避免并发双插
        dto.setFriendVerify(userSettings == null ? 1 : userSettings.getFriendVerify());
        return dto;
    }

    @Override
    public UserBriefDTO getUserBrief(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw AppException.userNotFound();
        }
        UserBriefDTO dto = new UserBriefDTO();
        dto.setUserId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        return dto;
    }

    @Override
    public List<UserBriefDTO> batchGetUsers(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (idList.isEmpty()) {
            return List.of();
        }
        Map<String, User> userMap = userMapper.selectByIds(idList).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (x, y) -> x));
        List<UserBriefDTO> dtos = new ArrayList<>(idList.size());
        for (String id : idList) {
            User user = userMap.get(id);
            if (user == null) {
                throw AppException.userNotFound();
            }
            UserBriefDTO dto = new UserBriefDTO();
            dto.setUserId(user.getId());
            dto.setNickname(user.getNickname());
            dto.setAvatar(user.getAvatar());
            dtos.add(dto);
        }
        return dtos;
    }

    @Override
    public UserInfoDTO getUserInfo(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw AppException.userNotFound();
        }
        UserInfoDTO dto = new UserInfoDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }
}
