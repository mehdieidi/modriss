package io.mehdieidi.varka.mdecli.service;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Orders Emfatic modules so imported sibling modules precede their dependents. */
public final class ModuleDependencyResolver {

  /**
   * Topologically sorts modules by their local Ecore import dependencies.
   *
   * @param modules modules to order
   * @return immutable dependency-first module order
   * @throws IllegalStateException when local imports form a cycle
   */
  public List<ModuleDescriptor> sort(Collection<ModuleDescriptor> modules) {
    Map<Path, ModuleDescriptor> byPath = new HashMap<>();
    Map<Path, Integer> indegrees = new HashMap<>();
    Map<Path, List<Path>> outgoing = new HashMap<>();

    for (ModuleDescriptor module : modules) {
      Path normalizedPath = module.sourceFile().toAbsolutePath().normalize();
      byPath.put(normalizedPath, module);
      indegrees.put(normalizedPath, 0);
      outgoing.put(normalizedPath, new ArrayList<>());
    }

    for (ModuleDescriptor module : modules) {
      Path modulePath = module.sourceFile().toAbsolutePath().normalize();
      for (String importedEcore : module.importedEcoreFiles()) {
        Path importedEmf = replaceWithSiblingEmf(module.sourceFile(), importedEcore);
        if (byPath.containsKey(importedEmf)) {
          outgoing.get(importedEmf).add(modulePath);
          indegrees.compute(modulePath, (key, value) -> value == null ? 1 : value + 1);
        }
      }
    }

    ArrayDeque<Path> queue =
        indegrees.entrySet().stream()
            .filter(entry -> entry.getValue() == 0)
            .map(Map.Entry::getKey)
            .sorted(Comparator.naturalOrder())
            .collect(ArrayDeque::new, ArrayDeque::add, ArrayDeque::addAll);

    List<ModuleDescriptor> sorted = new ArrayList<>();
    while (!queue.isEmpty()) {
      Path current = queue.removeFirst();
      sorted.add(byPath.get(current));
      for (Path dependent : outgoing.get(current)) {
        int nextValue = indegrees.computeIfPresent(dependent, (key, value) -> value - 1);
        if (nextValue == 0) {
          queue.add(dependent);
        }
      }
    }

    if (sorted.size() != modules.size()) {
      throw new IllegalStateException(
          "The Emfatic modules contain a circular dependency through .ecore imports.");
    }
    return List.copyOf(sorted);
  }

  /**
   * Resolves an imported Ecore name to a sibling {@code .emf} or {@code .emfatic} source.
   *
   * @param sourceFile importing module source
   * @param importedEcore imported Ecore path
   * @return normalized sibling source path
   */
  private Path replaceWithSiblingEmf(Path sourceFile, String importedEcore) {
    String fileName = Path.of(importedEcore).getFileName().toString();
    String sourceName = fileName.substring(0, fileName.length() - ".ecore".length()) + ".emf";
    Path sibling = sourceFile.toAbsolutePath().getParent().resolve(sourceName).normalize();
    if (!sibling.toFile().exists()) {
      sibling = sibling.resolveSibling(sourceName + "atic");
    }
    return sibling;
  }
}
