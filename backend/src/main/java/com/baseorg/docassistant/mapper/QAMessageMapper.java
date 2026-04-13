package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.config.typehandler.JsonbTypeHandler;
import com.baseorg.docassistant.entity.QAMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QAMessageMapper extends BaseMapper<QAMessage> {

    @Select("""
            SELECT * FROM qa_messages
            WHERE session_id = #{sessionId} AND user_id = #{userId}
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    @Results(id = "qaMessageResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "session_id", property = "sessionId"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "role", property = "role"),
            @Result(column = "content", property = "content"),
            @Result(column = "rewritten_query", property = "rewrittenQuery"),
            @Result(column = "intent", property = "intent"),
            @Result(column = "sources_json", property = "sourcesJson", typeHandler = JsonbTypeHandler.class),
            @Result(column = "confidence", property = "confidence"),
            @Result(column = "prompt_hash", property = "promptHash"),
            @Result(column = "status", property = "status"),
            @Result(column = "degraded", property = "degraded"),
            @Result(column = "degrade_reason", property = "degradeReason"),
            @Result(column = "fallback_mode", property = "fallbackMode"),
            @Result(column = "error_code", property = "errorCode"),
            @Result(column = "processing_time_ms", property = "processingTimeMs"),
            @Result(column = "plan_summary", property = "planSummary"),
            @Result(column = "tool_trace_json", property = "toolTraceJson", typeHandler = JsonbTypeHandler.class),
            @Result(column = "diagrams_json", property = "diagramsJson", typeHandler = JsonbTypeHandler.class),
            @Result(column = "external_sources_json", property = "externalSourcesJson", typeHandler = JsonbTypeHandler.class),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<QAMessage> findBySessionId(@Param("sessionId") Long sessionId,
                                    @Param("userId") Long userId,
                                    @Param("limit") int limit);
}
