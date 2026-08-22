package com.faust.easychat.dto.user.friend;

import com.faust.easychat.dto.user.UserBriefDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class FriendDTO extends UserBriefDTO {
    private String signature;
}
