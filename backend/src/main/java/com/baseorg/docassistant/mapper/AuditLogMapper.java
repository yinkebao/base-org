package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志 Mapper
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 按用户查询审计日志
     */
    @Select("SELECT * FROM audit_logs WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<AuditLog> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 按操作类型统计
     */
    @Select("SELECT action, COUNT(*) as count FROM audit_logs " +
            "WHERE created_at >= #{startTime} AND created_at <= #{endTime} " +
            "GROUP BY action")
    List<Map<String, Object>> countByAction(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * Token 消耗统计
     */
    @Select("SELECT SUM(token_cost) as total_tokens FROM audit_logs " +
            "WHERE created_at >= #{startTime} AND created_at <= #{endTime}")
    Long sumTokenCost(@Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);
}
