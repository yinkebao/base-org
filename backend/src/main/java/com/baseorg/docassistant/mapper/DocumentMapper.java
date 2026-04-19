package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    @Select("SELECT * FROM documents WHERE owner_id = #{ownerId} ORDER BY created_at DESC")
    List<Document> findByOwnerId(@Param("ownerId") Long ownerId);

    @Select("SELECT * FROM documents WHERE parent_id = #{parentId} ORDER BY created_at DESC")
    List<Document> findByParentId(@Param("parentId") Long parentId);

    @Select("SELECT * FROM documents WHERE id = #{id} AND owner_id = #{ownerId}")
    Document findByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
