package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
