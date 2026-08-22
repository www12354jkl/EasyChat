package com.faust.easychat.dto.chat;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class GroupDTO {

    private String id;

    private String name;

    private String avatar;

    private String ownerId;

    private Integer total;

    private String announcement;

    private Integer joinVerify;

    private List<Member> members = new ArrayList<>();

    @Data
    public static class Member {

        private String userId;

        private String nickname;

        private String avatar;

        private Integer role;
        
    }



}
