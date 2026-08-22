package com.faust.easychat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.easychat.entity.UserSettings;



@Mapper
public interface UserSettingsMapper extends BaseMapper<UserSettings> {

    @Select("SELECT * FROM user_settings WHERE user_id = #{userId}")
    UserSettings selectByUserId(String userId);

}
