package io.mehdieidi.modriss.platform.assistant.provider;

import io.mehdieidi.modriss.platform.assistant.config.AiProperties;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Checks the dedicated AI proxy without affecting non-AI outbound traffic. */
public class ProxyAvailability {

  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final AiProperties properties;
  private final java.util.Map<String, TimedCheck> cachedChecks = new ConcurrentHashMap<>();

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
    return check(properties.provider());
  }

  /** Checks the proxy settings used by the specified provider. */
  public ProxyCheck check(String provider) {
    String key = provider == null || provider.isBlank() ? "default" : provider;
    AiProperties.Proxy proxy = properties.proxyFor(provider);
    TimedCheck snapshot = cachedChecks.get(key);
    Instant now = Instant.now();
    if (snapshot != null && snapshot.checkedAt().plus(CACHE_TTL).isAfter(now))
      return snapshot.check();
    if (!proxy.enabled() || proxy.type() == AiProperties.ProxyType.DIRECT) {
      ProxyCheck resolved = new ProxyCheck(true, "AI proxy is disabled.");
      cachedChecks.put(key, new TimedCheck(resolved, now));
      return resolved;
    }
    ProxyCheck resolved = checkReachable(proxy);
    cachedChecks.put(key, new TimedCheck(resolved, now));
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

  private record TimedCheck(ProxyCheck check, Instant checkedAt) {}
}
