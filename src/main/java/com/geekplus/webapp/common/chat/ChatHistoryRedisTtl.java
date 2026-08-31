package com.geekplus.webapp.common.chat;

import com.geekplus.common.util.string.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * AI 对话 Redis 会话 TTL 与 guest 判定（统一流式/非流式/DB 回填）。
 *
 * <p>登录用户：Redis 为热缓存，TTL 与 DB 回填一致（5 天），避免「刚聊完 24h、从库恢复 5d」双标准。
 * guest：Redis 24 小时；同时落库供后台查询（与登录用户共用 insert 路径）。
 */
public final class ChatHistoryRedisTtl {

    private ChatHistoryRedisTtl() {
    }

    /** guest 临时会话（仅 Redis） */
    public static final long GUEST_SESSION_TTL = 24L;
    public static final TimeUnit GUEST_SESSION_TTL_UNIT = TimeUnit.HOURS;

    /** 登录用户会话热缓存（写入与 hydrate 一致） */
    public static final long MEMBER_SESSION_TTL = 5L;
    public static final TimeUnit MEMBER_SESSION_TTL_UNIT = TimeUnit.DAYS;

    /** 是否访客：空 / guest / 含 guest 子串（与历史代码一致） */
    public static boolean isGuestUser(String username) {
        if (StringUtils.isEmpty(username)) {
            return true;
        }
        return "guest".equals(username) || username.contains("guest");
    }

    /**
     * 按用户类型刷新 redisKey 过期时间（每次 append 消息后调用）。
     */
    public static void refreshSessionExpire(StringRedisTemplate redis, String redisKey, String username) {
        if (redis == null || StringUtils.isEmpty(redisKey)) {
            return;
        }
        if (isGuestUser(username)) {
            redis.expire(redisKey, GUEST_SESSION_TTL, GUEST_SESSION_TTL_UNIT);
        } else {
            redis.expire(redisKey, MEMBER_SESSION_TTL, MEMBER_SESSION_TTL_UNIT);
        }
    }
}
