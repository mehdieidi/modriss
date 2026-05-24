package io.mehdieidi.modless.backend.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modless")
public record BackendProperties(Path storageRoot, Duration sessionTtl,
                                List<String> allowedOrigins) {

}
