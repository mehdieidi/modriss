package io.mehdieidi.modriss.backend.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bounds background SMTP work for password reset messages. */
@Configuration
class PasswordResetMailConfig {

  @Bean("passwordResetMailExecutor")
  ThreadPoolTaskExecutor passwordResetMailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("password-reset-mail-");
    return executor;
  }
}
