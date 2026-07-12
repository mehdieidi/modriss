package io.mehdieidi.varka.platform.modeling.xmi;

/**
 * Controls how strictly JSON-to-XMI export reports unresolved references and invalid attributes.
 *
 * @param strictReferences whether reference conversion errors fail export
 * @param strictAttributes whether attribute conversion errors fail export
 */
record XmiExportOptions(boolean strictReferences, boolean strictAttributes) {

  /**
   * Returns the default strict export behavior.
   *
   * @return strict export options
   */
  static XmiExportOptions strict() {
    return new XmiExportOptions(true, true);
  }
}
