package com.geekplus.webapp.tool.job.mapper;

import com.geekplus.webapp.tool.job.entity.JobPosting;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

/**
 * 岗位库访问。表不存在时查询会失败——部署前执行 sql/job_posting.sql。
 * Worker / 搜索会 catch 后降级为「仅深链+AI」。
 */
@Mapper
public interface JobPostingMapper {

    @Select("<script>"
            + "SELECT id, source, source_id AS sourceId, title, company, city, salary, summary, "
            + "requirements, url, industry, tags, fetched_at AS fetchedAt, expire_at AS expireAt, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM job_posting WHERE 1=1 "
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (title LIKE CONCAT('%',#{keyword},'%') OR summary LIKE CONCAT('%',#{keyword},'%') "
            + " OR tags LIKE CONCAT('%',#{keyword},'%') OR company LIKE CONCAT('%',#{keyword},'%')) "
            + "</if>"
            + "<if test='city != null and city != \"\"'>"
            + " AND (city LIKE CONCAT('%',#{city},'%') OR city = 'Remote' OR city = '远程') "
            + "</if>"
            + "<if test='industry != null and industry != \"\"'>"
            + " AND (industry LIKE CONCAT('%',#{industry},'%') OR tags LIKE CONCAT('%',#{industry},'%')) "
            + "</if>"
            + " AND (expire_at IS NULL OR expire_at &gt; NOW()) "
            + " ORDER BY fetched_at DESC LIMIT #{limit}"
            + "</script>")
    List<JobPosting> search(@Param("keyword") String keyword,
                            @Param("city") String city,
                            @Param("industry") String industry,
                            @Param("limit") int limit);

    @Insert("INSERT INTO job_posting(source, source_id, title, company, city, salary, summary, requirements, "
            + "url, industry, tags, fetched_at, expire_at, created_at, updated_at) "
            + "VALUES(#{source}, #{sourceId}, #{title}, #{company}, #{city}, #{salary}, #{summary}, #{requirements}, "
            + "#{url}, #{industry}, #{tags}, #{fetchedAt}, #{expireAt}, #{createdAt}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE title=VALUES(title), company=VALUES(company), city=VALUES(city), "
            + "salary=VALUES(salary), summary=VALUES(summary), requirements=VALUES(requirements), "
            + "url=VALUES(url), industry=VALUES(industry), tags=VALUES(tags), "
            + "fetched_at=VALUES(fetched_at), expire_at=VALUES(expire_at), updated_at=VALUES(updated_at)")
    int upsert(JobPosting posting);

    @Delete("DELETE FROM job_posting WHERE expire_at IS NOT NULL AND expire_at < #{before}")
    int deleteExpired(@Param("before") Date before);

    @Select("SELECT COUNT(1) FROM job_posting")
    long countAll();
}
