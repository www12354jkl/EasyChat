package com.faust.easychat.dto.chat;

import java.util.ArrayList;
import java.util.List;

import com.faust.easychat.dto.user.UserBriefDTO;

import lombok.Data;

@Data
public class GroupInfoDTO {

    private String id;

    private String name;

    private String avatar;

    private String ownerId;

    private String ownerName;

    private String announcement;

    private Integer joinVerify;

    private List<UserBriefDTO> members = new ArrayList<>();


}
