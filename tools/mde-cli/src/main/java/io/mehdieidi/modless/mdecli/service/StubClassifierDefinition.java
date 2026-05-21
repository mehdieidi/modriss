package io.mehdieidi.modless.mdecli.service;

import java.util.List;

public record StubClassifierDefinition(String kind, String name,
                                       List<StubFeatureDefinition> features) {

}
