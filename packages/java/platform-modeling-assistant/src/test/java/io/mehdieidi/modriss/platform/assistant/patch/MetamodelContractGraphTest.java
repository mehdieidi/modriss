package io.mehdieidi.modriss.platform.assistant.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.modeling.metamodel.FileMetamodelResolver;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
import org.junit.jupiter.api.Test;

class MetamodelContractGraphTest {
  @Test
  void derivesInheritedStructuralContractsFromCombinedEcore() {
    MetamodelContractGraph graph =
        new MetamodelContractGraph(
            new FileMetamodelResolver(new MdeRuntimePaths(MdeRuntimeOptions.defaults())));

    MetamodelContractGraph.Snapshot first = graph.forLevel(ModelLevel.CIM);
    MetamodelContractGraph.Snapshot second = graph.forLevel(ModelLevel.CIM);

    assertEquals(first.sha256(), second.sha256());
    assertTrue(first.sha256().matches("[0-9a-f]{64}"));
    assertFalse(first.types().isEmpty());
    assertTrue(first.types().containsKey("Actor"));
    assertTrue(
        first.types().get("Actor").attributes().stream()
            .anyMatch(attribute -> attribute.name().equals("name")));
  }
}
