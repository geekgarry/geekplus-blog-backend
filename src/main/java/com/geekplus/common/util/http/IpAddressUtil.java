package com.geekplus.common.util.http;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekplus.common.config.WebAppConfig;
import com.geekplus.common.constant.Constant;
import com.geekplus.common.redis.RedisUtil;
import com.geekplus.common.util.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lionsoul.ip2region.xdb.Searcher;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * IP 归属地：优先本地/Redis 缓存，外网查询短超时，避免拖慢登录等主路径。
 */
@Slf4j
public class IpAddressUtil {
    public static String XDB_PATH = "D:\\workspace\\java\\src\\main\\resources\\ip\\ip2region.xdb";

    public static final String IP_URL = "https://whois.pconline.com.cn/ipJson.jsp";

    private static final String REDIS_IP_ADDR_PREFIX = "ip:addr:";
    private static final long REDIS_TTL_SECONDS = TimeUnit.HOURS.toSeconds(24);
    private static final long LOCAL_TTL_MS = TimeUnit.HOURS.toMillis(6);
    private static final int CONNECT_TIMEOUT_MS = 400;
    private static final int READ_TIMEOUT_MS = 400;

    private static final ConcurrentHashMap<String, CachedAddr> LOCAL_CACHE = new ConcurrentHashMap<>();

    private static final class CachedAddr {
        final String addr;
        final long expireAt;

        CachedAddr(String addr, long ttlMs) {
            this.addr = addr;
            this.expireAt = System.currentTimeMillis() + ttlMs;
        }

        boolean valid() {
            return System.currentTimeMillis() < expireAt;
        }
    }

    public static String getRealAddressByIP(String ip) {
        String address = "XX XX";
        if (IPUtils.internalIp(ip)) {
            return "内网IP";
        }
        if (StringUtils.isEmpty(ip)) {
            return address;
        }

        CachedAddr local = LOCAL_CACHE.get(ip);
        if (local != null && local.valid()) {
            return local.addr;
        }

        String fromRedis = getFromRedis(ip);
        if (StringUtils.isNotEmpty(fromRedis)) {
            LOCAL_CACHE.put(ip, new CachedAddr(fromRedis, LOCAL_TTL_MS));
            return fromRedis;
        }

        if (WebAppConfig.isAddressEnabled()) {
            try {
                URL url = new URL(IP_URL + "?ip=" + ip + "&json=true");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("content-type", "application/json; charset=utf-8");
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, Constant.GBK));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String rspStr = sb.toString();
                if (StringUtils.isEmpty(rspStr.trim())) {
                    log.warn("获取地理位置为空 {}", ip);
                    return cacheAndReturn(ip, "UNKNOWN");
                }
                JSONObject obj = JSONObject.parseObject(rspStr);
                String region = obj.getString("pro");
                String city = obj.getString("city");
                address = String.format("%s %s", region, city);
                if (com.geekplus.common.util.string.StringUtils.isEmpty(region)) {
                    address = obj.getString("addr");
                }
                if (StringUtils.isEmpty(address)) {
                    address = "UNKNOWN";
                }
                return cacheAndReturn(ip, address);
            } catch (Exception e) {
                log.warn("获取地理位置超时/失败 ip={} err={}", ip, e.getMessage());
                return cacheAndReturn(ip, "UNKNOWN");
            }
        }
        return address;
    }

    private static String cacheAndReturn(String ip, String address) {
        // 失败结果短缓存，避免长时间把瞬时超时当成最终结果
        boolean unknown = "UNKNOWN".equals(address) || "XX XX".equals(address);
        long localTtl = unknown ? TimeUnit.MINUTES.toMillis(2) : LOCAL_TTL_MS;
        long redisTtl = unknown ? TimeUnit.MINUTES.toSeconds(2) : REDIS_TTL_SECONDS;
        LOCAL_CACHE.put(ip, new CachedAddr(address, localTtl));
        putRedis(ip, address, redisTtl);
        return address;
    }

    private static String getFromRedis(String ip) {
        try {
            RedisUtil redisUtil = SpringUtil.getBean(RedisUtil.class);
            if (redisUtil == null) {
                return null;
            }
            Object v = redisUtil.get(REDIS_IP_ADDR_PREFIX + ip);
            return v != null ? String.valueOf(v) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void putRedis(String ip, String address, long ttlSeconds) {
        try {
            RedisUtil redisUtil = SpringUtil.getBean(RedisUtil.class);
            if (redisUtil != null && StringUtils.isNotEmpty(address)) {
                redisUtil.set(REDIS_IP_ADDR_PREFIX + ip, address, ttlSeconds);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 获取mac地址
     */
    public static String getMacIpAddress() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            byte[] macAddressBytes = NetworkInterface.getByInetAddress(inetAddress).getHardwareAddress();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < macAddressBytes.length; i++) {
                if (i != 0) {
                    sb.append("-");
                }
                String s = Integer.toHexString(macAddressBytes[i] & 0xFF);
                sb.append(s.length() == 1 ? 0 + s : s);
            }
            return sb.toString().trim().toUpperCase();
        } catch (Exception e) {
            log.error("Mac获取IP地址异常,{}", e.getMessage());
        }
        return "";
    }

    public static String getIpPossessionByFile(String ip) {
        if (StringUtils.isNotEmpty(ip)) {
            try {
                Searcher searcher = Searcher.newWithFileOnly(XDB_PATH);
                long sTime = System.nanoTime();
                String region = searcher.search(ip);
                long cost = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - sTime);
                region = region.replace("|0", "");
                return region;
            } catch (Exception e) {
                log.error("获取IP地址异常：{} ", e.getMessage());
                throw new RuntimeException("获取IP地址异常");
            }
        }
        return "未知";
    }

    public static String getCityInfoByVectorIndex(String ip) {
        if (StringUtils.isNotEmpty(ip)) {
            try {
                byte[] vIndex = Searcher.loadVectorIndexFromFile(XDB_PATH);
                Searcher searcher = Searcher.newWithVectorIndex(XDB_PATH, vIndex);
                String region = searcher.search(ip);
                region = region.replace("|0", "");
                return region;
            } catch (Exception e) {
                log.error("获取IP地址异常：{} ", e.getMessage());
                throw new RuntimeException("获取IP地址异常");
            }
        }
        return "未知";
    }

    public static String getCityInfoByMemorySearch(String ip) {
        if (StringUtils.isNotEmpty(ip)) {
            try {
                byte[] cBuff = Searcher.loadContentFromFile(XDB_PATH);
                Searcher searcher = Searcher.newWithBuffer(cBuff);
                String region = searcher.search(ip);
                region = region.replace("|0", "");
                return region;
            } catch (Exception e) {
                log.error("获取IP地址异常：{} ", e.getMessage());
                throw new RuntimeException("获取IP地址异常");
            }
        }
        return "未知";
    }

    public static String getIpAddressByOnline(String ip) {
        try {
            URL url = new URL("http://ip-api.com/json/" + ip + "?lang=zh-CN");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("content-type", "application/json; charset=utf-8");
            connection.connect();

            InputStream inputStream = connection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            String str = sb.toString();
            if (StringUtils.isNotEmpty(str)) {
                Map<String, Object> map = new HashMap<>();
                ObjectMapper mapper = new ObjectMapper();
                map = mapper.readValue(str, Map.class);
                String countryCode = (String) map.get("countryCode");
                String country = (String) map.get("country");
                String city = (String) map.get("city");
                String regionName = (String) map.get("regionName");
                if ("CN".equals(countryCode)) {
                    return String.format("%s %s", regionName, city);
                } else {
                    return String.format("%s %s", country, regionName);
                }
            }
        } catch (Exception e) {
            log.error("在线查询IP地址异常，{}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
        return null;
    }

    public static String getIpPossession(String ipAddress) {
        if (StringUtils.isNotEmpty(ipAddress)) {
            ipAddress = ipAddress.replace("|", " ");
            String[] cityList = ipAddress.split(" ");
            if (cityList.length > 0) {
                if ("中国".equals(cityList[0])) {
                    if (cityList.length > 1) {
                        return cityList[1];
                    }
                }
                return cityList[0];
            }
        }
        return "未知";
    }
}
