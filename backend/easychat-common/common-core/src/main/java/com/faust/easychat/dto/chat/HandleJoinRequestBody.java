package com.faust.easychat.dto.chat;

import lombok.Data;

@Data
public class HandleJoinRequestBody {

    /** 1=同意 2=拒绝 */
    private Integer status;
}
