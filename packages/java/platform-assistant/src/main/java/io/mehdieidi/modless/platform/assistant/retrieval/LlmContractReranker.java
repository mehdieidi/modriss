package io.mehdieidi.modless.platform.assistant.retrieval;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore.ContractSearchHit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Lightweight LLM rerank pass over hybrid contract retrieval candidates. */
public class LlmContractReranker {

  private final AssistantModelProvider provider;
  private final boolean enabled;

  public LlmContractReranker(AssistantModelProvider provider) {
    this(provider, false);
  }

  public LlmContractReranker(AssistantModelProvider provider, boolean enabled) {
    this.provider = provider;
    this.enabled = enabled;
  }

  public List<ContractSearchHit> rerank(
      RetrievalPlan plan, List<ContractSearchHit> candidates, int limit) {
    if (!enabled
        || provider == null
        || !provider.available()
        || candidates == null
        || candidates.size() <= limit
        || limit <= 0) {
      return candidates == null
          ? List.of()
          : candidates.stream().limit(Math.max(0, limit)).toList();
    }
    StringBuilder prompt = new StringBuilder();
    prompt.append("Rank the following metamodel contract ids for this modeling task.\n");
    prompt.append("Task summary: ").append(plan.taskSummary()).append('\n');
    prompt
        .append("Candidate types: ")
        .append(String.join(", ", plan.candidateTypes()))
        .append('\n');
    prompt.append("Concepts: ").append(String.join(", ", plan.concepts())).append('\n');
    prompt.append("Return only a JSON array of ids in best-first order.\n");
    int index = 0;
    Map<String, ContractSearchHit> byId = new LinkedHashMap<>();
    for (ContractSearchHit candidate : candidates) {
      String id = "c" + index++;
      byId.put(id, candidate);
      prompt.append(id).append(": ").append(candidate.title()).append('\n');
    }
    try {
      AssistantModelProvider.AssistantReply reply =
          provider.complete(
              new AssistantModelProvider.AssistantPrompt(
                  AssistantModelRole.PLANNER,
                  "You rerank metamodel contracts for retrieval. Return only JSON.",
                  prompt.toString(),
                  List.of()));
      List<String> ordered = parseIds(reply.content());
      LinkedHashSet<ContractSearchHit> ranked = new LinkedHashSet<>();
      for (String id : ordered) {
        ContractSearchHit hit = byId.get(id);
        if (hit != null) {
          ranked.add(hit);
        }
      }
      candidates.forEach(ranked::add);
      return ranked.stream().limit(limit).toList();
    } catch (Exception ignored) {
      return candidates.stream().limit(limit).toList();
    }
  }

  private List<String> parseIds(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    String trimmed = content.trim();
    int start = trimmed.indexOf('[');
    int end = trimmed.lastIndexOf(']');
    if (start < 0 || end <= start) {
      return List.of();
    }
    String body = trimmed.substring(start + 1, end);
    List<String> ids = new ArrayList<>();
    for (String token : body.split(",")) {
      String id = token.replace("\"", "").trim();
      if (!id.isBlank()) {
        ids.add(id);
      }
    }
    return ids;
  }
}
