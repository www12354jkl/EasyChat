package com.faust.easychat.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.easychat.entity.Conversation;
import com.faust.easychat.entity.ConversationMember;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

        @Select("SELECT * FROM conversation_member WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
        ConversationMember selectByConversationAndUser(@Param("conversationId") String conversationId,
                        @Param("userId") String userId);

        /** 批量插入成员（同角色） */
        @Insert("""
                        <script>
                        INSERT INTO conversation_member (conversation_id, user_id, role, created_at, last_read_at) VALUES
                        <foreach collection="ids" item="uid" separator=",">
                            (#{conversationId}, #{uid}, #{role}, #{createdAt}, #{createdAt})
                        </foreach>
                        </script>
                        """)
        int batchInsert(@Param("conversationId") String conversationId,
                        @Param("ids") List<String> ids,
                        @Param("role") Integer role,
                        @Param("createdAt") LocalDateTime createdAt);

        @Delete("""
                        <script>
                        DELETE FROM conversation_member
                        WHERE conversation_id = #{conversationId}
                          AND user_id IN
                        <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                            #{userId}
                        </foreach>
                        </script>
                            """)
        void batchDelete(@Param("conversationId") String conversationId,
                        @Param("userIds") List<String> userIds);

        @Delete("DELETE FROM conversation_member WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
        void deleteByConversationAndUser(@Param("conversationId") String conversationId,
                        @Param("userId") String userId);

        /** 批量统计多个群的人数：返回 [{conversation_id, total}] */
        @Select("""
                        <script>
                        SELECT conversation_id, COUNT(*) AS total
                        FROM conversation_member
                        WHERE conversation_id IN
                        <foreach collection="ids" item="cid" open="(" separator="," close=")">#{cid}</foreach>
                        GROUP BY conversation_id
                        </script>
                        """)
        List<Map<String, Object>> countByConversationIds(@Param("ids") List<String> ids);

        @Select("""
                        SELECT c.id, c.name, c.avatar
                        FROM conversation_member m
                        JOIN conversation c ON m.conversation_id = c.id
                        WHERE m.user_id = #{userId}
                          AND c.owner_id != #{userId}
                          AND c.type = 2
                        ORDER BY m.created_at DESC
                        """)
        List<Conversation> selectJoinedConversations(@Param("userId") String userId);

        @Select("""
                        SELECT *
                        FROM conversation_member
                        WHERE conversation_id = #{conversationId}
                        ORDER BY created_at DESC
                        """)
        List<ConversationMember> selectConversationAllMembers(@Param("conversationId") String conversationId);

        @Select("""
                        <script>
                        SELECT * FROM conversation_member
                        WHERE conversation_id IN
                        <foreach collection="ids" item="cid" open="(" separator="," close=")">
                        #{cid}
                        </foreach>
                        </script>
                        """)
        List<ConversationMember> selectMemberByConversationIds(@Param("ids") List<String> ids);

        /** 查某用户所有会话里的其他成员 id（去重，用于在线状态广播） */
        @Select("""
                        SELECT DISTINCT m2.user_id
                        FROM conversation_member m1
                        JOIN conversation_member m2 ON m1.conversation_id = m2.conversation_id
                        WHERE m1.user_id = #{userId}
                          AND m2.user_id != #{userId}
                        """)
        List<String> selectPeerIds(@Param("userId") String userId);

        @Select("""
                        <script>
                        SELECT m.conversation_id AS conversation_id, COUNT(*) AS unread
                        FROM message msg
                        JOIN conversation_member m ON msg.conversation_id = m.conversation_id
                        WHERE m.user_id = #{userId}
                          AND m.conversation_id IN
                        <foreach collection="ids" item="cid" open="(" separator="," close=")">#{cid}</foreach>
                          AND msg.sender_id != #{userId}
                          AND msg.created_at > COALESCE(m.last_read_at, m.created_at)
                          AND (m.cleared_at IS NULL OR msg.created_at > m.cleared_at)
                        GROUP BY m.conversation_id
                        </script>
                        """)
        List<Map<String, Object>> countUnreadByConversationIds(@Param("userId") String userId,
                        @Param("ids") List<String> ids);

        @Update("UPDATE conversation_member SET last_read_at = #{now} WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
        int markRead(@Param("conversationId") String conversationId, @Param("userId") String userId,
                        @Param("now") LocalDateTime now);

        @Update("UPDATE conversation_member SET cleared_at = #{now} WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
        int markCleared(@Param("conversationId") String conversationId, @Param("userId") String userId,
                        @Param("now") LocalDateTime now);
}
