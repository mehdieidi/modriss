package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Incrementally builds large semantic patches in validated phases for complex creation requests.
 */
@Service
public class AssistantPhasedPatchBuilder {

  private final AssistantPatchCompiler patchCompiler;
  private final AssistantDomainScaffoldService scaffolds;

  public AssistantPhasedPatchBuilder(
      AssistantPatchCompiler patchCompiler, AssistantDomainScaffoldService scaffolds) {
    this.patchCompiler = patchCompiler;
    this.scaffolds = scaffolds;
  }

  /**
   * Merges scaffold phases and optional refinement packs into one semantic patch.
   *
   * @param level modeling level
   * @param request user message
   * @param baseModel planning base model
   * @return phased semantic patch when supported
   */
  public Optional<SemanticModelPatch> buildPhased(
      ModelLevel level, String request, JsonNode baseModel) {
    Optional<SemanticModelPatch> scaffold = scaffolds.build(level, request);
    if (scaffold.isEmpty()) {
      return Optional.empty();
    }
    List<SemanticModelPatch.Operation> operations = new ArrayList<>(scaffold.get().operations());
    if (containsAny(request, "observability", "monitoring", "alerting", "tracing", "metrics")) {
      operations.addAll(scaffolds.observabilityPack(level, request).operations());
    }
    if (containsAny(request, "security", "encryption", "auth", "authorization", "policy")) {
      operations.addAll(scaffolds.securityPack(level, request).operations());
    }
    SemanticModelPatch merged = new SemanticModelPatch(operations);
    try {
      patchCompiler.compile(baseModel, merged);
      return Optional.of(merged);
    } catch (Exception ex) {
      return scaffold;
    }
  }

  private boolean containsAny(String request, String... keywords) {
    if (request == null) {
      return false;
    }
    String normalized = request.toLowerCase(Locale.ROOT);
    for (String keyword : keywords) {
      if (normalized.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
