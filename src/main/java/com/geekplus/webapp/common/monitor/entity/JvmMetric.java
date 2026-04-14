package com.geekplus.webapp.common.monitor.entity;

/**
 * author     : geekplus
 * email      :
 * date       : 4/11/26 8:10 PM
 * description: //TODO
 */
public class JvmMetric {
    private Long id;
    private Double cpuUsage;
    private Long heapUsed;
    private Long heapMax;
    private Integer threadCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Long getHeapUsed() {
        return heapUsed;
    }

    public void setHeapUsed(Long heapUsed) {
        this.heapUsed = heapUsed;
    }

    public Long getHeapMax() {
        return heapMax;
    }

    public void setHeapMax(Long heapMax) {
        this.heapMax = heapMax;
    }

    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }
}
