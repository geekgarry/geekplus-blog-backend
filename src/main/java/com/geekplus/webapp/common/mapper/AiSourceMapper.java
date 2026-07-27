package com.geekplus.webapp.common.mapper;

import com.geekplus.webapp.common.entity.AiSource;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiSourceMapper {

    @Select("SELECT * FROM ai_source ORDER BY sort_order ASC, id ASC")
    List<AiSource> findAll();

    @Select("SELECT * FROM ai_source WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<AiSource> findEnabled();

    @Select("SELECT * FROM ai_source WHERE id = #{id}")
    AiSource findById(Long id);

    @Select("SELECT * FROM ai_source WHERE enabled = 1 AND is_default = 1 LIMIT 1")
    AiSource findDefault();

    @Select("SELECT * FROM ai_source WHERE enabled = 1 AND provider = #{provider} ORDER BY is_default DESC, sort_order ASC, id ASC LIMIT 1")
    AiSource findEnabledByProvider(String provider);

    @Insert("INSERT INTO ai_source(name, provider, model, api_key, api_url, enabled, is_default, sort_order, remark, created_at, updated_at) " +
            "VALUES(#{name}, #{provider}, #{model}, #{apiKey}, #{apiUrl}, #{enabled}, #{isDefault}, #{sortOrder}, #{remark}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiSource source);

    @Update("UPDATE ai_source SET name=#{name}, provider=#{provider}, model=#{model}, api_key=#{apiKey}, api_url=#{apiUrl}, " +
            "enabled=#{enabled}, is_default=#{isDefault}, sort_order=#{sortOrder}, remark=#{remark}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(AiSource source);

    @Update("UPDATE ai_source SET is_default = 0")
    int clearDefault();

    @Update("UPDATE ai_source SET is_default = 1, updated_at = NOW() WHERE id = #{id}")
    int setDefault(Long id);

    @Delete("DELETE FROM ai_source WHERE id = #{id}")
    int deleteById(Long id);
}
