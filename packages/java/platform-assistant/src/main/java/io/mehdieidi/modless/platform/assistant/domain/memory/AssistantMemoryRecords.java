package io.mehdieidi.modless.platform.assistant.domain.memory;

import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.time.Instant;
import java.util.Map;

/** Durable assistant conversation, proposal, and audit records. */
public final class AssistantMemoryRecords {

  private AssistantMemoryRecords() {}

  /**
   * Assistant thread record.
   *
   * @param id thread ID
   * @param userId owner user ID
   * @param projectId project ID
   * @param level model level
   * @param title thread title
   * @param activeModelId active model ID
   * @param activeRevision active model revision
   * @param createdAt creation timestamp
   * @param updatedAt update timestamp
   */
  public record ThreadRecord(
      String id,
      String userId,
      String projectId,
      ModelLevel level,
      String title,
      String activeModelId,
      Long activeRevision,
      Instant createdAt,
      Instant updatedAt) {}

  /**
   * Assistant message record.
   *
   * @param id message ID
   * @param threadId owning thread ID
   * @param role message role
   * @param content message content
   * @param metadata message metadata
   * @param createdAt creation time
   */
  public record MessageRecord(
      String id,
      String threadId,
      String role,
      String content,
      Map<String, Object> metadata,
      Instant createdAt) {}

  /**
   * Stored proposal record.
   *
   * @param id proposal ID
   * @param threadId thread ID
   * @param projectId project ID
   * @param modelId model ID
   * @param modelRevision model revision
   * @param proposal proposal payload
   * @param status proposal status
   * @param decidedAt decision time
   */
  public record ProposalRecord(
      String id,
      String threadId,
      String projectId,
      String modelId,
      long modelRevision,
      AssistantProposal proposal,
      String status,
      Instant decidedAt) {}

  /**
   * Conversation list entry for history browsing.
   *
   * @param sessionId durable session/thread ID
   * @param title display title
   * @param preview first user message or summary snippet
   * @param updatedAt last activity timestamp
   * @param messageCount total stored messages
   */
  public record ConversationSummary(
      String sessionId, String title, String preview, Instant updatedAt, int messageCount) {}
}
