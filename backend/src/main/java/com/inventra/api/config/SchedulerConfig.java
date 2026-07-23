package com.inventra.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * <p>Replaces Spring's default single-thread {@code @EnableScheduling} executor with
 * a small thread pool.
 *
 * <p>Spring's default task scheduler has exactly one
 * thread. If a {@code @Scheduled} method throws an uncaught exception that exception
 * is swallowed (after our try/catch fix), but if the task takes longer than its
 * fixed-rate interval the next execution is simply delayed — a slow task can starve all
 * other scheduled tasks indefinitely.
 *
 * <p>There are currently two scheduled tasks:
 * {@code RefreshTokenCleanupService.purgeExpiredTokens} and
 * {@code AuditLogRetentionService.anonymiseExpiredAuditPii}. The pool is sized to 4 so
 * that adding a task does not silently make two jobs contend for a thread — at pool size
 * 2 (one thread per existing task) a single slow job could already delay the other.
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("inventra-scheduler-");
        // Propagate uncaught exceptions to the log rather than silently discarding them.
        scheduler.setErrorHandler(t ->
                org.slf4j.LoggerFactory.getLogger(SchedulerConfig.class)
                        .error("Uncaught exception in scheduled task", t));
        scheduler.initialize();
        return scheduler;
    }
}
