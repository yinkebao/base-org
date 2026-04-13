package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.entity.QASession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QASessionMapper extends BaseMapper<QASession> {

    @Select("""
            SELECT * FROM qa_sessions
            WHERE user_id = #{userId}
            ORDER BY COALESCE(last_message_at, created_at) DESC
            LIMIT #{limit}
            """)
    List<QASession> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
