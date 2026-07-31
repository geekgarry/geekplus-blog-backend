package com.geekplus.webapp.tool.resume.mapper;

import com.geekplus.webapp.tool.resume.entity.ResumeTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import java.util.List;

@Mapper
public interface ResumeTemplateMapper {
    @Select("SELECT * FROM resume_template ORDER BY id DESC")
    List<ResumeTemplate> findAll();

    @Select("SELECT * FROM resume_template WHERE id = #{id}")
    ResumeTemplate findById(Long id);

    @Select("SELECT * FROM resume_template WHERE `key` = #{key}")
    ResumeTemplate findByKey(String key);

    @Insert("INSERT INTO resume_template(`key`, name, description, layout_json, is_vip, readonly) VALUES(#{key}, #{name}, #{description}, #{layoutJson}, #{isVip}, #{readonly})")
    int insert(ResumeTemplate template);

    @Update("UPDATE resume_template SET `key` = #{key}, name = #{name}, description = #{description}, layout_json = #{layoutJson}, is_vip = #{isVip}, readonly = #{readonly} WHERE id = #{id}")
    int update(ResumeTemplate template);

    @Delete("DELETE FROM resume_template WHERE id = #{id}")
    int delete(Long id);
}
