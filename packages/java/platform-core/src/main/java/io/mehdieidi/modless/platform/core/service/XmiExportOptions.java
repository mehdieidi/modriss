package io.mehdieidi.modless.platform.core.service;

record XmiExportOptions(boolean strictReferences, boolean strictAttributes) {

    static XmiExportOptions strict() {
        return new XmiExportOptions(true, true);
    }
}
