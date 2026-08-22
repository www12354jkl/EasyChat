package com.faust.easychat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.easychat.entity.Friend;
import com.faust.easychat.entity.User;

@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

    @Select("""
            SELECT * FROM user u
            JOIN friend f ON f.friend_id = u.id
            WHERE f.user_id = #{userId}
            ORDER BY f.created_at
            """)
    List<User> listFriends(String userId);

    @Delete("""
            DELETE FROM friend WHERE user_id = #{userId} AND friend_id = #{friendId}
            """)
    void deleteByUserIdAndFriendUserId(@Param("userId") String userId, @Param("friendId") String friendId);

    @Select("SELECT * FROM friend WHERE user_id = #{userId} AND friend_id = #{friendId}")
    Friend selectByUserIdAndFriendUserId(@Param("userId") String userId, @Param("friendId") String friendId);
}
