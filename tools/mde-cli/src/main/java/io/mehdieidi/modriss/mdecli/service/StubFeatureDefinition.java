package io.mehdieidi.modriss.mdecli.service;

/**
 * Minimal structural feature metadata used to generate bootstrap Ecore resources.
 *
 * @param kind Emfatic feature kind
 * @param name feature name
 */
public record StubFeatureDefinition(String kind, String name) {}
