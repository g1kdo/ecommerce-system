package rw.smart.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executor for access-log writes.
 *
 * The monitoring aspect runs on every service call, and its MongoDB insert was
 * costing each request a network round trip it did not need to wait for. Moving
 * the write off the request thread is the single largest latency win available
 * in the monitoring path.
 *
 * The queue is bounded and the rejection policy discards. That combination is
 * chosen on purpose: if logging cannot keep up, the correct outcome is to lose
 * log lines, not to slow down or fail the orders that produced them.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String ACCESS_LOG_EXECUTOR = "accessLogExecutor";

    @Bean(name = ACCESS_LOG_EXECUTOR)
    public Executor accessLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("access-log-");
        executor.setRejectedExecutionHandler((task, runner) ->
                log.warn("Access-log queue is full; dropping one entry"));
        // Do not hold shutdown open waiting for log writes to drain.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
