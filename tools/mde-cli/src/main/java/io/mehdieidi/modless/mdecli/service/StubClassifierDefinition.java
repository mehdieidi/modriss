package io.mehdieidi.modless.mdecli.service;

import java.util.List;

/**
 * Minimal classifier metadata used to generate bootstrap Ecore resources.
 *
 * @param kind Emfatic classifier kind
 * @param name classifier name
 * @param features declared structural features
 */
public record StubClassifierDefinition(
    String kind, String name, List<StubFeatureDefinition> features) {}
