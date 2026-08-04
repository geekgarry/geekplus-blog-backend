package com.geekplus.webapp.function.entity;

import java.util.Date;

/**
 * 站点每日运营汇总 gp_site_daily_stats
 */
public class GpSiteDailyStats {
    private Date statDate;
    private Long pv;
    private Long uv;
    private Long newViews;
    private Long newLikes;
    private Long newComments;
    private Integer newArticles;
    private Date createdAt;
    private Date updatedAt;

    public Date getStatDate() {
        return statDate;
    }

    public void setStatDate(Date statDate) {
        this.statDate = statDate;
    }

    public Long getPv() {
        return pv;
    }

    public void setPv(Long pv) {
        this.pv = pv;
    }

    public Long getUv() {
        return uv;
    }

    public void setUv(Long uv) {
        this.uv = uv;
    }

    public Long getNewViews() {
        return newViews;
    }

    public void setNewViews(Long newViews) {
        this.newViews = newViews;
    }

    public Long getNewLikes() {
        return newLikes;
    }

    public void setNewLikes(Long newLikes) {
        this.newLikes = newLikes;
    }

    public Long getNewComments() {
        return newComments;
    }

    public void setNewComments(Long newComments) {
        this.newComments = newComments;
    }

    public Integer getNewArticles() {
        return newArticles;
    }

    public void setNewArticles(Integer newArticles) {
        this.newArticles = newArticles;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
