package io.mehdieidi.varka.platform.assistant.provider;

import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

/** Checks the dedicated AI proxy without affecting non-AI outbound traffic. */
public class ProxyAvailability {

  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final AiProperties properties;
  private volatile ProxyCheck cachedCheck;
  private volatile Instant cachedAt = Instant.EPOCH;

  /**
   * Creates the proxy checker.
   *
   * @param properties AI settings
   */
  public ProxyAvailability(AiProperties properties) {
    this.properties = properties;
  }

  /**
   * Returns whether AI requests can reach the configured local proxy.
   *
   * @return availability result
   */
  public ProxyCheck check() {
    AiProperties.Proxy proxy = properties.proxy();
    if (!proxy.enabled() || proxy.type() == AiProperties.ProxyType.DIRECT) {
      return cachedOrResolve(new ProxyCheck(true, "AI proxy is disabled."));
    }
    Instant now = Instant.now();
    ProxyCheck snapshot = cachedCheck;
    if (snapshot != null && cachedAt.plus(CACHE_TTL).isAfter(now)) {
      return snapshot;
    }
    ProxyCheck resolved = checkReachable(proxy);
    cachedCheck = resolved;
    cachedAt = now;
    return resolved;
  }

  private ProxyCheck cachedOrResolve(ProxyCheck resolved) {
    Instant now = Instant.now();
    ProxyCheck snapshot = cachedCheck;
    if (snapshot != null && cachedAt.plus(CACHE_TTL).isAfter(now)) {
      return snapshot;
    }
    cachedCheck = resolved;
    cachedAt = now;
    return resolved;
  }

  private ProxyCheck checkReachable(AiProperties.Proxy proxy) {
    InetSocketAddress address = proxy.address();
    try (Socket socket = new Socket()) {
      socket.connect(address, Math.toIntExact(proxy.connectTimeout().toMillis()));
      return new ProxyCheck(
          true,
          "AI proxy is reachable at " + address.getHostString() + ":" + address.getPort() + ".");
    } catch (Exception ex) {
      return new ProxyCheck(
          false,
          "AI proxy is not reachable at "
              + address.getHostString()
              + ":"
              + address.getPort()
              + ".");
    }
  }

  /**
   * Proxy check result.
   *
   * @param available whether the proxy is reachable or not required
   * @param message diagnostic message
   */
  public record ProxyCheck(boolean available, String message) {}
}
