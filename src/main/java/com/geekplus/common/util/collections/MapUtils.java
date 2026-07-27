package com.geekplus.common.util.collections;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * author     : geekplus
 * email      :
 * date       : 5/18/26 6:51 PM
 * description: //TODO
 */
public class MapUtils {

    /**
     * 单键值对‌：使用 Collections.singletonMap(key, value)
     * 优点：轻量、不可变、线程安全
     * 限制：仅支持 ‌1 个键值对‌，且 ‌不允许 null 键或值‌ ‌‌
     * 多键值对（推荐）‌：手动创建 HashMap 并用 Collections.unmodifiableMap() 包装
     */

    public static <K, V> Map<K, V> of(K k1, V v1) {
        return Collections.singletonMap(k1, v1);
    }
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1); map.put(k2, v2);
        return Collections.unmodifiableMap(map);
    }
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1); map.put(k2, v2); map.put(k3, v3);
        return Collections.unmodifiableMap(map);
    }
    // 可继续重载至 10 个参数
}
