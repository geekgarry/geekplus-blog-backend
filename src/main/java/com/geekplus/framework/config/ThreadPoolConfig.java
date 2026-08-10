package com.geekplus.framework.config;

import com.geekplus.common.util.Threads;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步延时任务线程池：供 {@link com.geekplus.framework.manager.AsyncManager} 使用。
 * （MVC 用的 threadPoolTaskExecutor 已在 WebMvcResourceConfig 中定义，此处不重复。）
 */
@Configuration
public class ThreadPoolConfig {

    private final int corePoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());

    @Bean(name = "scheduledExecutorService")
    public ScheduledExecutorService scheduledExecutorService() {
        AtomicInteger idx = new AtomicInteger(1);
        return new ScheduledThreadPoolExecutor(corePoolSize, r -> {
            Thread t = new Thread(r, "schedule-pool-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                Threads.printException(r, t);
            }
        };
    }
}
