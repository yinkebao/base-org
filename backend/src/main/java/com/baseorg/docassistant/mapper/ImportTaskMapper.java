package com.baseorg.docassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baseorg.docassistant.entity.ImportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 导入任务 Mapper
 */
@Mapper
public interface ImportTaskMapper extends BaseMapper<ImportTask> {

    @Select("SELECT * FROM import_tasks WHERE task_id = #{taskId}")
    ImportTask findByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM import_tasks WHERE task_id = #{taskId} AND owner_id = #{ownerId}")
    ImportTask findByTaskIdAndOwnerId(@Param("taskId") String taskId, @Param("ownerId") Long ownerId);

    @Select("SELECT * FROM import_tasks WHERE owner_id = #{ownerId} ORDER BY created_at DESC")
    List<ImportTask> findByOwnerId(@Param("ownerId") Long ownerId);
}
