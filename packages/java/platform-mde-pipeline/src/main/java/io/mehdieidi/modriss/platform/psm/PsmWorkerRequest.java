package io.mehdieidi.modriss.platform.psm;

/** Wire contract sent only to a disposable execution container. */
public record PsmWorkerRequest(
    String operation,
    PsmPlatformPackage platform,
    byte[] sourceXmi,
    byte[] pimEcore,
    tools.jackson.databind.JsonNode modelJson) {
  public PsmWorkerRequest(
      String operation, PsmPlatformPackage platform, byte[] sourceXmi, byte[] pimEcore) {
    this(operation, platform, sourceXmi, pimEcore, null);
  }
}
