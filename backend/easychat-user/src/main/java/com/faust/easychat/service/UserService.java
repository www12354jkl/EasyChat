package com.faust.easychat.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.dto.user.ProfileDTO;
import com.faust.easychat.dto.user.ResetPasswordRequest;
import com.faust.easychat.dto.user.SettingsRequest;
import com.faust.easychat.dto.user.UpdateProfileRequest;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.dto.user.UserInfoDTO;

public interface UserService {

    ProfileDTO getProfileById(String id);

    SettingsRequest getSettings(String userId);

    void updateProfileById(String id, UpdateProfileRequest request);

    String uploadAvatar(String id, MultipartFile file);

    void resetPassword(String id, ResetPasswordRequest request);

    void sendResetCode(String id);

    void updateFriendVerify(String userId, SettingsRequest request);

    UserBriefDTO getUserBrief(String id);

    List<UserBriefDTO> batchGetUsers(String ids);

    UserInfoDTO getUserInfo(String userId);

}
