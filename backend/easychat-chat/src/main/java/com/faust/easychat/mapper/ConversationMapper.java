package com.faust.easychat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

}
