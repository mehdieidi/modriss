package io.mehdieidi.modless.platform.assistant.source;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits source material into bounded chunks without using document grammar for intent routing. */
public class SourceChunker {

  private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,3} .+$");
  private static final Pattern USER_STORY_HEADING =
      Pattern.compile("(?m)^###\\s+US-\\d+\\b.*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern USER_STORY_LINE =
      Pattern.compile("(?m)^As an? \\w[\\w\\s-]{0,80},\\b", Pattern.CASE_INSENSITIVE);

  /** Creates approximate token-bounded chunks. */
  public List<SourceChunk> chunk(String sourceName, String content, int maxTokens, int maxChunks) {
    String text = content == null ? "" : content.trim();
    if (text.isBlank()) {
      return List.of();
    }
    int maxChars = Math.max(800, maxTokens <= 0 ? 16000 : maxTokens * 4);
    int chunkLimit = Math.max(1, maxChunks <= 0 ? 24 : maxChunks);
    List<String> sections = splitSemanticSections(text);
    List<String> parts = packSections(sections, maxChars, chunkLimit);
    List<SourceChunk> result = new ArrayList<>();
    for (int index = 0; index < parts.size(); index++) {
      result.add(
          new SourceChunk(
              "chunk-" + (index + 1),
              sourceName == null || sourceName.isBlank() ? "source" : sourceName,
              parts.get(index),
              index + 1,
              parts.size(),
              SourceChunk.CoverageState.COMPRESSED));
    }
    return List.copyOf(result);
  }

  private List<String> splitSemanticSections(String text) {
    List<Integer> boundaries = new ArrayList<>();
    boundaries.add(0);
    collectBoundaries(USER_STORY_HEADING, text, boundaries);
    if (boundaries.size() <= 1) {
      collectBoundaries(USER_STORY_LINE, text, boundaries);
    }
    if (boundaries.size() <= 1) {
      collectBoundaries(MARKDOWN_HEADING, text, boundaries);
    }
    if (boundaries.size() <= 1) {
      return splitParagraphSections(text);
    }
    boundaries.add(text.length());
    List<String> sections = new ArrayList<>();
    for (int index = 0; index < boundaries.size() - 1; index++) {
      int start = boundaries.get(index);
      int end = boundaries.get(index + 1);
      if (end <= start) {
        continue;
      }
      String section = text.substring(start, end).trim();
      if (!section.isBlank()) {
        sections.add(section);
      }
    }
    return sections.isEmpty() ? List.of(text) : sections;
  }

  private void collectBoundaries(Pattern pattern, String text, List<Integer> boundaries) {
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      int start = matcher.start();
      if (start > 0 && boundaries.stream().noneMatch(existing -> existing == start)) {
        boundaries.add(start);
      }
    }
    boundaries.sort(Integer::compareTo);
  }

  private List<String> splitParagraphSections(String text) {
    String[] paragraphs = text.split("\\n\\n+");
    List<String> sections = new ArrayList<>();
    for (String paragraph : paragraphs) {
      String trimmed = paragraph.trim();
      if (!trimmed.isBlank()) {
        sections.add(trimmed);
      }
    }
    return sections.isEmpty() ? List.of(text) : sections;
  }

  private List<String> packSections(List<String> sections, int maxChars, int chunkLimit) {
    if (sections.size() > 1
        && sections.stream().allMatch(section -> section.length() <= maxChars)) {
      List<String> parts = new ArrayList<>();
      for (String section : sections) {
        if (parts.size() >= chunkLimit) {
          break;
        }
        parts.add(section);
      }
      return parts.isEmpty() ? List.of(sections.get(0)) : parts;
    }
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String section : sections) {
      if (section.length() > maxChars) {
        flushPart(parts, current, chunkLimit);
        parts.addAll(splitLargeSection(section, maxChars, chunkLimit - parts.size()));
        if (parts.size() >= chunkLimit) {
          return parts.subList(0, chunkLimit);
        }
        continue;
      }
      int projected = current.length() + (current.isEmpty() ? 0 : 2) + section.length();
      if (!current.isEmpty() && projected > maxChars) {
        flushPart(parts, current, chunkLimit);
        if (parts.size() >= chunkLimit) {
          return parts;
        }
      }
      if (!current.isEmpty()) {
        current.append("\n\n");
      }
      current.append(section);
    }
    flushPart(parts, current, chunkLimit);
    return parts.isEmpty() ? List.of(sections.get(0)) : parts;
  }

  private void flushPart(List<String> parts, StringBuilder current, int chunkLimit) {
    if (current.isEmpty() || parts.size() >= chunkLimit) {
      return;
    }
    parts.add(current.toString().trim());
    current.setLength(0);
  }

  private List<String> splitLargeSection(String section, int maxChars, int remainingSlots) {
    if (remainingSlots <= 0) {
      return List.of();
    }
    List<String> parts = new ArrayList<>();
    int offset = 0;
    while (offset < section.length() && parts.size() < remainingSlots) {
      int end = Math.min(section.length(), offset + maxChars);
      if (end < section.length()) {
        int paragraph = section.lastIndexOf("\n\n", end);
        if (paragraph > offset + maxChars / 2) {
          end = paragraph;
        }
      }
      parts.add(section.substring(offset, end).trim());
      offset = end;
    }
    return parts;
  }
}
