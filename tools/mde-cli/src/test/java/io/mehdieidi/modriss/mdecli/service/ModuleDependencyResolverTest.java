package io.mehdieidi.modriss.mdecli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests dependency-first ordering of modular Emfatic sources. */
class ModuleDependencyResolverTest {

  private final ModuleDependencyResolver resolver = new ModuleDependencyResolver();

  /** Verifies that imported modules appear before their dependents. */
  @Test
  void sortsModulesSoImportedPackagesComeFirst() {
    ModuleDescriptor root =
        new ModuleDescriptor(
            Path.of("cim-root.emf"), List.of("cim-kernel.ecore", "cim-types.ecore"));
    ModuleDescriptor kernel = new ModuleDescriptor(Path.of("cim-kernel.emf"), List.of());
    ModuleDescriptor types =
        new ModuleDescriptor(Path.of("cim-types.emf"), List.of("cim-kernel.ecore"));

    List<ModuleDescriptor> sorted = resolver.sort(List.of(root, types, kernel));

    assertEquals(kernel, sorted.get(0));
    assertEquals(3, sorted.size());
  }
}
