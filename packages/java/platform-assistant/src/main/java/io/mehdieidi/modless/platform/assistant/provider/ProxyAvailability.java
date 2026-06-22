package io.mehdieidi.modless.platform.assistant.provider;

import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Checks the dedicated AI proxy without affecting non-AI outbound traffic. */
public class ProxyAvailability {

  private final AiProperties properties;

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
      return new ProxyCheck(true, "AI proxy is disabled.");
    }
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
