package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.entity.Template;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模板 Mapper
 */
@Mapper
public interface TemplateMapper extends BaseMapper<Template> {

    /**
     * 查询活跃模板
     */
    @Select("SELECT * FROM templates WHERE is_active = true ORDER BY category, name")
    List<Template> findActiveTemplates();

    /**
     * 按分类查询模板
     */
    @Select("SELECT * FROM templates WHERE category = #{category} AND is_active = true ORDER BY name")
    List<Template> findByCategory(@Param("category") String category);
}
