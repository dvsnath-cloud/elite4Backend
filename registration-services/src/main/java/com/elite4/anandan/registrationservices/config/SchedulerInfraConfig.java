package com.elite4.anandan.registrationservices.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for scheduler and async infrastructure.
 *
 * - RestTemplate with connect/read timeouts (prevents hanging on notification-services)
 * - Notification thread pool for async dispatch (bounded queue to prevent OOM)
 * - Scheduler thread pool so @Scheduled jobs don't block each other
 */
@Configuration
public class SchedulerInfraConfig implements AsyncConfigurer {

    @Value("${scheduler.resttemplate.connect-timeout-ms:5000}")
    private int connectTimeout;

    @Value("${scheduler.resttemplate.read-timeout-ms:10000}")
    private int readTimeout;

    @Value("${scheduler.notification.pool.core-size:20}")
    private int notifCorePoolSize;

    @Value("${scheduler.notification.pool.max-size:50}")
    private int notifMaxPoolSize;

    @Value("${scheduler.notification.pool.queue-capacity:5000}")
    private int notifQueueCapacity;

    @Value("${scheduler.pool.size:3}")
    private int schedulerPoolSize;

    /**
     * RestTemplate with timeouts — replaces the default no-timeout bean.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    /**
     * Thread pool for @Async notification dispatch.
     * 20 core threads, max 50, queue 5000 — handles bursts of 100K notifications.
     *
     * CallerRunsPolicy: if queue is full, the calling thread (scheduler thread)
     * runs the notification itself — acts as natural back-pressure instead of
     * throwing TaskRejectedException which would lose the notification entirely.
     */
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(notifCorePoolSize);
        executor.setMaxPoolSize(notifMaxPoolSize);
        executor.setQueueCapacity(notifQueueCapacity);
        executor.setThreadNamePrefix("notif-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    /**
     * Scheduler thread pool — allows multiple @Scheduled jobs to run concurrently.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(300);
        return scheduler;
    }
}
