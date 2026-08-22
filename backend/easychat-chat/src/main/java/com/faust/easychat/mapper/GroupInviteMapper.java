package com.faust.easychat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.easychat.entity.GroupInvite;

@Mapper
public interface GroupInviteMapper extends BaseMapper<GroupInvite> {

    @Select("""
            SELECT * FROM group_invite
            WHERE invite_conversation_id = #{conversationId}
            LIMIT 1
            """)
    GroupInvite selectByInviteConversationId(@Param("conversationId") String conversationId);

    @Select("""
            SELECT * FROM group_invite
            WHERE group_id = #{groupId}
              AND invitee_id = #{inviteeId}
              AND status IN (0, 2)
            LIMIT 1
            """)
    GroupInvite selectActiveByGroupAndInvitee(@Param("groupId") String groupId,
            @Param("inviteeId") String inviteeId);

    @Select("""
            SELECT * FROM group_invite
            WHERE group_id = #{groupId}
              AND invitee_id = #{inviteeId}
              AND status = 2
            LIMIT 1
            """)
    GroupInvite selectWaitingByGroupAndInvitee(@Param("groupId") String groupId,
            @Param("inviteeId") String inviteeId);
}
