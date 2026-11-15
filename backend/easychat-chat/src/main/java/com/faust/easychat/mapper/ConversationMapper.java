package com.faust.easychat.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.easychat.entity.Conversation;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("""
            SELECT * FROM conversation
            WHERE
                owner_id = #{ownerId}
            ORDER BY created_at DESC
            """)
    List<Conversation> selectByOwner(@Param("ownerId") String ownerId);

    @Select("""
            SELECT * FROM conversation c
            JOIN conversation_member m ON c.id = m.conversation_id
            WHERE m.user_id = #{userId} AND c.status = 1
            ORDER BY COALESCE(c.last_message_at, c.created_at) DESC
            """)
    List<Conversation> selectMyConversations(@Param("userId") String userId);

    @Update("""
            UPDATE conversation
            SET last_message = #{lastMessage},
                last_message_at = #{lastMessageAt},
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND (last_message_at IS NULL OR last_message_at <= #{lastMessageAt})
            """)
    int updateLastMessageIfNotNewer(@Param("id") String id,
            @Param("lastMessage") String lastMessage,
            @Param("lastMessageAt") LocalDateTime lastMessageAt,
            @Param("updatedAt") LocalDateTime updatedAt);

}
