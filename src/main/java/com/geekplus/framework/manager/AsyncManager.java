package com.geekplus.framework.manager;

import com.geekplus.common.util.Threads;
import com.geekplus.common.util.spring.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务管理器：延迟调度登录日志、操作日志、缓存预热等。
 */
public class AsyncManager {

    private static final Logger log = LoggerFactory.getLogger(AsyncManager.class);

    private static final int OPERATE_DELAY_TIME = 10;

    private static final AsyncManager INSTANCE = new AsyncManager();

    private volatile ScheduledExecutorService executor;

    private AsyncManager() {
    }

    public static AsyncManager me() {
        return INSTANCE;
    }

    private ScheduledExecutorService executor() {
        ScheduledExecutorService e = executor;
        if (e != null) {
            return e;
        }
        synchronized (this) {
            if (executor == null) {
                try {
                    executor = SpringUtil.getBean("scheduledExecutorService");
                } catch (Exception ex) {
                    log.warn("未找到 scheduledExecutorService Bean，使用兜底线程池: {}", ex.getMessage());
                    executor = new ScheduledThreadPoolExecutor(4, r -> {
                        Thread t = new Thread(r, "async-fallback-" + System.currentTimeMillis());
                        t.setDaemon(true);
                        return t;
                    }, new ThreadPoolExecutor.CallerRunsPolicy());
                }
            }
            return executor;
        }
    }

    public void execute(TimerTask task) {
        executor().schedule(task, OPERATE_DELAY_TIME, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        Threads.shutdownAndAwaitTermination(executor());
    }
}
