package com.faust.easychat.dto.user.friend;

import java.io.Serializable;

import lombok.Data;

@Data
public class FriendRequestHandledDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /** 1=同意 2=拒绝 */
    private Integer status;

    /** 处理人（被申请人） */
    private String userId;

    private String nickname;

    private String avatar;
}
