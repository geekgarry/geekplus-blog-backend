package com.geekplus.webapp.common.monitor.mapper;

import com.geekplus.webapp.common.monitor.entity.JvmMetric;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * author     : geekplus
 * email      :
 * date       : 4/11/26 8:12 PM
 * description: TODO
 */
@Mapper
public interface JvmMetricMapper {
    @Insert("INSERT INTO jvm_metrics (cpu_usage, heap_used, heap_max, thread_count, record_time) " +
            "VALUES (#{cpuUsage}, #{heapUsed}, #{heapMax}, #{threadCount}, NOW())")
    int insert(JvmMetric metric);
}
