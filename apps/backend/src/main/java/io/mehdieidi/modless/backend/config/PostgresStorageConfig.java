package io.mehdieidi.modless.backend.config;

import io.mehdieidi.modless.platform.storage.api.PlatformStore;
import io.mehdieidi.modless.platform.storage.postgres.PostgresPlatformStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the production PostgreSQL persistence adapter. */
@Configuration
public class PostgresStorageConfig {

  @Bean
  PlatformStore platformStore(
      JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    return new PostgresPlatformStore(jdbcTemplate, new TransactionTemplate(transactionManager));
  }
}
