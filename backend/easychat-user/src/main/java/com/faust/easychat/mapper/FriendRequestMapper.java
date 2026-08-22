package com.faust.easychat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.easychat.entity.FriendRequest;

@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequest> {

    @Select("""
            SELECT * FROM friend_request
            WHERE to_user_id = #{toUserId} AND status = 0
            ORDER BY created_at DESC
            """)
    List<FriendRequest> selectPendingByToUserId(@Param("toUserId") String toUserId);

}
