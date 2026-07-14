package io.mehdieidi.varka.backend;

import io.mehdieidi.varka.backend.admin.AdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Starts the Varka backend and enables its scheduled maintenance tasks. */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AdminProperties.class)
public class BackendApplication {

  /**
   * Launches the Spring Boot application.
   *
   * @param args command-line arguments passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
