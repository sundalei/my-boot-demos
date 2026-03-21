package com.example.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

  @Bean
  RateLimiter notionRateLimiter() {
    // The Notion API free tier is limited to 3 requests per second, so our RateLimiter is
    // configured accordingly.
    // A timeout is set for permit waiting to handle request bursts.
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(3)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(15))
            .build();

    return RateLimiter.of("notionRateLimiter", config);
  }
}
