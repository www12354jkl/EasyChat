package com.faust.easychat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.easychat.entity.ConversationJoinRequest;

@Mapper
public interface ConversationJoinRequestMapper extends BaseMapper<ConversationJoinRequest> {

    @Select("""
            SELECT * FROM conversation_join_request
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND status = 0
            LIMIT 1
            """)
    ConversationJoinRequest selectPendingByGroupAndUser(@Param("conversationId") String conversationId,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT r.* FROM conversation_join_request r
            JOIN conversation c ON r.conversation_id = c.id
            JOIN conversation_member me ON c.id = me.conversation_id AND me.user_id = #{userId}
            WHERE r.status = 0
              AND c.type = 2
              AND (c.owner_id = #{userId} OR me.role = 2)
            <if test="conversationId != null and conversationId != ''">
              AND r.conversation_id = #{conversationId}
            </if>
            ORDER BY r.created_at DESC
            </script>
            """)
    List<ConversationJoinRequest> selectPendingForAdmin(@Param("userId") String userId,
            @Param("conversationId") String conversationId);
}

