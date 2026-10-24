package com.blob.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blob.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
