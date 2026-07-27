package com.geekplus.webapp.tool.resume.mapper;

import com.geekplus.webapp.tool.resume.entity.ResumeData;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 兼容现有表结构：user_id / title / data_json / updated_at
 * templateKey 存在 data_json 的 __templateId 字段中（见 ResumeService）
 */
@Mapper
public interface ResumeDataMapper {
    @Select("SELECT id, user_id AS userId, title, data_json AS dataJson, updated_at AS updatedAt " +
            "FROM resume_data WHERE user_id = #{userId} ORDER BY updated_at DESC LIMIT 1")
    ResumeData findLatestByUserId(Long userId);

    @Select("SELECT id, user_id AS userId, title, data_json AS dataJson, updated_at AS updatedAt " +
            "FROM resume_data WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<ResumeData> findListByUserId(Long userId);

    @Select("SELECT id, user_id AS userId, title, data_json AS dataJson, updated_at AS updatedAt " +
            "FROM resume_data ORDER BY updated_at DESC")
    List<ResumeData> findAll();

    @Select("SELECT id, user_id AS userId, title, data_json AS dataJson, updated_at AS updatedAt " +
            "FROM resume_data WHERE id = #{id}")
    ResumeData findById(Long id);

    @Insert("INSERT INTO resume_data(user_id, title, data_json, updated_at) " +
            "VALUES(#{userId}, #{title}, #{dataJson}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ResumeData resumeData);

    @Update("UPDATE resume_data SET title = #{title}, data_json = #{dataJson}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(ResumeData resumeData);

    @Delete("DELETE FROM resume_data WHERE id = #{id}")
    int deleteById(Long id);
}
