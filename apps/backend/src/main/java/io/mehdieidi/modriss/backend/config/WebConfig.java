package io.mehdieidi.modriss.backend.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configures cross-origin access for the backend HTTP API. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final BackendProperties properties;

  /**
   * Creates the web configuration.
   *
   * @param properties backend settings that supply the allowed origins
   */
  public WebConfig(BackendProperties properties) {
    this.properties = properties;
  }

  /**
   * Applies the configured origins and supported API methods to every route.
   *
   * @param registry registry used to define CORS mappings
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> origins = properties.allowedOrigins();
    registry
        .addMapping("/**")
        .allowedOrigins(origins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("Content-Disposition", "X-Request-Id")
        .allowCredentials(true);
  }
}
