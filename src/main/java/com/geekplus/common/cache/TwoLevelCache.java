package com.geekplus.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.geekplus.common.redis.RedisUtil;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Caffeine(L1) + Redis(L2) 双重缓存。
 * 读：L1 → L2 → loader；写/失效：先删 L2 再删 L1（多实例靠 L1 短 TTL 兜底）。
 */
@Component
public class TwoLevelCache {

    private final Cache<String, Object> local = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();

    @Resource
    private RedisUtil redisUtil;

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        Object localVal = local.getIfPresent(key);
        if (localVal != null) {
            return (T) localVal;
        }
        Object redisVal = redisUtil.get(key);
        if (redisVal != null) {
            local.put(key, redisVal);
            return (T) redisVal;
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded);
        }
        return loaded;
    }

    public void put(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        redisUtil.set(key, value);
        local.put(key, value);
    }

    public void put(String key, Object value, long seconds) {
        if (key == null || value == null) {
            return;
        }
        redisUtil.set(key, value, seconds);
        local.put(key, value);
    }

    public void evict(String key) {
        if (key == null) {
            return;
        }
        redisUtil.del(key);
        local.invalidate(key);
    }

    public void evictLocal(String key) {
        if (key != null) {
            local.invalidate(key);
        }
    }

    public void clearLocal() {
        local.invalidateAll();
    }
}
