package com.example.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;

@Configuration
public class ResilienceConfig {

  private static final Logger LOG = LoggerFactory.getLogger(ResilienceConfig.class);

  @Bean
  RateLimiter notionRateLimiter(RateLimiterRegistry registry) {
    // The Notion API free tier is limited to 3 requests per second, so our RateLimiter is
    // configured accordingly.
    // A timeout is set for permit waiting to handle request bursts.
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(3)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(15))
            .build();

    return registry.rateLimiter("notionRateLimiter", config);
  }

  @Bean
  Retry notionRetry(RetryRegistry registry) {
    RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(2))
            .failAfterMaxAttempts(true)
            // Retry on RestClientException which may occur due to transient network issues or API
            // errors.
            .retryExceptions(HttpServerErrorException.class)
            .build();

    Retry retry = registry.retry("notionRetry", config);

    retry
        .getEventPublisher()
        .onRetry(
            event ->
                LOG.warn(
                    "⚠️ RETRY TRIGGERED! Attempt {}/3 due to: {}",
                    event.getNumberOfRetryAttempts(),
                    event.getLastThrowable().getMessage()));

    return retry;
  }
}
