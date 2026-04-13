package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.entity.Chunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档块 Mapper
 */
@Mapper
public interface ChunkMapper extends BaseMapper<Chunk> {

    @Select("SELECT * FROM chunks WHERE doc_id = #{docId} ORDER BY chunk_index")
    List<Chunk> findByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM chunks WHERE doc_id = #{docId}")
    void deleteByDocId(@Param("docId") Long docId);

    @Select("SELECT COUNT(*) FROM chunks WHERE doc_id = #{docId}")
    int countByDocId(@Param("docId") Long docId);
}
