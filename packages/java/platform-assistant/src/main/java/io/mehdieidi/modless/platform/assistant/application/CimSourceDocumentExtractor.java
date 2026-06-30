package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts structured CIM source evidence from user-story and event-storming documents. */
final class CimSourceDocumentExtractor {

  private static final Pattern STORY =
      Pattern.compile("(?i)^\\s*(?:\\d+[.)]\\s*)?As\\s+(?:an?|the)\\s+([^,]+),\\s*I\\s+(.+)$");
  private static final Pattern LIST_PREFIX = Pattern.compile("^\\s*(?:[-*]|\\d+[.)])\\s+");
  private static final Pattern CAMEL = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

  private final ObjectMapper mapper;

  CimSourceDocumentExtractor(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  Optional<String> extract(String sourceName, String sourceContent) {
    if (sourceContent == null || sourceContent.isBlank()) {
      return Optional.empty();
    }
    ObjectNode root = mapper.createObjectNode();
    ArrayNode elements = root.putArray("elements");
    ArrayNode notes = root.putArray("coverageNotes");
    Set<String> seen = new LinkedHashSet<>();
    List<StoryFact> stories = new ArrayList<>();

    Section section = Section.OTHER;
    for (String rawLine : sourceContent.split("\\R")) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (line.isBlank()) {
        continue;
      }
      Section detected = sectionFromHeading(line);
      if (detected != Section.OTHER || isHeading(line)) {
        section = detected;
        continue;
      }
      switch (section) {
        case STAKEHOLDERS -> extractStakeholderGoal(line, elements, seen);
        case GOALS ->
            bulletText(line)
                .ifPresent(item -> addElement(elements, seen, "BusinessGoal", item, item, line));
        case STORIES -> extractStory(line, elements, seen, stories);
        case COMMANDS ->
            splitList(line)
                .forEach(item -> addElement(elements, seen, "Command", item, item, line));
        case EVENTS ->
            splitList(line)
                .forEach(item -> addElement(elements, seen, "BusinessEvent", item, item, line));
        case ACCEPTANCE -> extractAcceptanceCriterion(line, elements, seen);
        case POLICIES ->
            bulletText(line)
                .ifPresent(
                    item ->
                        addElement(elements, seen, "Policy", titleFromSentence(item), item, line));
        case EXTERNAL_SYSTEMS ->
            bulletText(line)
                .ifPresent(
                    item ->
                        addElement(
                            elements, seen, "ExternalSystem", subjectBeforeVerb(item), item, line));
        case QUERIES ->
            splitList(line).forEach(item -> addElement(elements, seen, "Query", item, item, line));
        case RISKS ->
            bulletText(line)
                .ifPresent(
                    item ->
                        addElement(elements, seen, "Risk", titleFromSentence(item), item, line));
        case OTHER -> extractInlineStory(line, elements, seen, stories);
      }
    }

    for (StoryFact story : stories) {
      addElement(
          elements, seen, "Actor", story.actor(), "Actor from user story", story.sourceLine());
      addElement(
          elements,
          seen,
          "BusinessCapability",
          story.capability(),
          "Capability required by user story: " + story.sourceLine(),
          story.sourceLine());
      addElement(
          elements,
          seen,
          "DomainEntity",
          domainEntityFromCapability(story.capability()),
          "Domain concept implied by user story: " + story.sourceLine(),
          story.sourceLine());
      addElement(
          elements, seen, "Command", story.capability(), story.sourceLine(), story.sourceLine());
      addElement(
          elements,
          seen,
          "BusinessEvent",
          eventNameFromCapability(story.capability()),
          story.sourceLine(),
          story.sourceLine());
      if (looksLikeQueryNeed(story.capability())) {
        addElement(
            elements, seen, "Query", story.capability(), story.sourceLine(), story.sourceLine());
      }
      addElement(
          elements,
          seen,
          "BusinessProcess",
          "Process: " + story.capability(),
          "Business process covering user story: " + story.sourceLine(),
          story.sourceLine());
    }

    if (elements.size() < 8) {
      return Optional.empty();
    }
    notes.add(
        "Extracted "
            + elements.size()
            + " CIM evidence elements from "
            + (sourceName == null || sourceName.isBlank() ? "attached source document" : sourceName)
            + " before invoking the planner.");
    root.putArray("coverageGaps");
    return Optional.of(root.toString());
  }

  private void extractStakeholderGoal(String line, ArrayNode elements, Set<String> seen) {
    Optional<String> text = bulletText(line);
    if (text.isEmpty()) {
      return;
    }
    String value = text.get();
    String lower = value.toLowerCase(Locale.ROOT);
    int wants = lower.indexOf(" wants ");
    if (wants > 0) {
      String stakeholder = cleanName(value.substring(0, wants));
      String goal = cleanName(value.substring(wants + " wants ".length()));
      addElement(elements, seen, "Stakeholder", stakeholder, value, line);
      addElement(elements, seen, "BusinessGoal", titleFromSentence(goal), value, line);
      return;
    }
    addElement(elements, seen, "Stakeholder", subjectBeforeVerb(value), value, line);
  }

  private void extractStory(
      String line, ArrayNode elements, Set<String> seen, List<StoryFact> stories) {
    Optional<String> text = bulletText(line);
    text.ifPresent(value -> extractInlineStory(value, elements, seen, stories));
  }

  private void extractInlineStory(
      String line, ArrayNode elements, Set<String> seen, List<StoryFact> stories) {
    String lower = line.toLowerCase(Locale.ROOT);
    int wants = lower.indexOf(" wants ");
    if (wants > 0 && !lower.startsWith("as ")) {
      String stakeholder = cleanName(line.substring(0, wants));
      String goal = cleanName(line.substring(wants + " wants ".length()));
      addElement(elements, seen, "Stakeholder", stakeholder, line, line);
      addElement(elements, seen, "BusinessGoal", titleFromSentence(goal), line, line);
      return;
    }
    Matcher matcher = STORY.matcher(line);
    if (!matcher.find()) {
      return;
    }
    String actor = cleanName(matcher.group(1));
    String capability = capabilityFromStoryTail(matcher.group(2));
    stories.add(new StoryFact(actor, capability, line));
    addElement(elements, seen, "Requirement", capability, line, line);
  }

  private void extractAcceptanceCriterion(String line, ArrayNode elements, Set<String> seen) {
    Optional<String> text = bulletText(line);
    if (text.isEmpty()) {
      return;
    }
    String item = text.get();
    String lower = item.toLowerCase(Locale.ROOT);
    if (lower.startsWith("risk:")) {
      addElement(elements, seen, "Risk", titleFromSentence(item.substring(5)), item, line);
    } else if (lower.startsWith("assumption:")) {
      addElement(elements, seen, "Assumption", titleFromSentence(item.substring(11)), item, line);
    } else if (lower.startsWith("hotspot:")) {
      addElement(elements, seen, "Hotspot", titleFromSentence(item.substring(8)), item, line);
    } else {
      addElement(elements, seen, "Requirement", titleFromSentence(item), item, line);
      if (lower.contains(" cannot ")
          || lower.contains(" only ")
          || lower.contains(" must ")
          || lower.contains(" when ")) {
        addElement(elements, seen, "Policy", titleFromSentence(item), item, line);
      }
    }
  }

  private List<String> splitList(String line) {
    String value = bulletText(line).orElse(line);
    List<String> result = new ArrayList<>();
    for (String part : value.split(",")) {
      String item = cleanName(part);
      if (!item.isBlank()) {
        result.add(item);
      }
    }
    return result;
  }

  private Optional<String> bulletText(String line) {
    if (line == null || line.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(LIST_PREFIX.matcher(line).replaceFirst("").trim());
  }

  private void addElement(
      ArrayNode elements,
      Set<String> seen,
      String type,
      String name,
      String summary,
      String sourceExcerpt) {
    String clean = cleanName(name);
    if (clean.isBlank()) {
      return;
    }
    String key = type + ":" + clean.toLowerCase(Locale.ROOT);
    if (!seen.add(key)) {
      return;
    }
    ObjectNode element = elements.addObject();
    element.put("sourceKey", slug(type + "-" + clean));
    element.put("type", type);
    element.put("name", clean);
    element.put("summary", summary == null || summary.isBlank() ? clean : summary.trim());
    element.put("description", summary == null || summary.isBlank() ? clean : summary.trim());
    element.put("sourceExcerpt", truncate(sourceExcerpt, 240));
  }

  private Section sectionFromHeading(String line) {
    String normalized = line.toLowerCase(Locale.ROOT).replace("#", "").trim();
    if (normalized.equals("goals") || normalized.endsWith(" goals")) {
      return Section.GOALS;
    }
    if (normalized.contains("stakeholder")) {
      return Section.STAKEHOLDERS;
    }
    if (normalized.contains("user stories") || normalized.equals("stories")) {
      return Section.STORIES;
    }
    if (normalized.contains("acceptance criteria")) {
      return Section.ACCEPTANCE;
    }
    if (normalized.contains("commands")) {
      return Section.COMMANDS;
    }
    if (normalized.contains("business events") || normalized.equals("events")) {
      return Section.EVENTS;
    }
    if (normalized.contains("policies") || normalized.contains("rules")) {
      return Section.POLICIES;
    }
    if (normalized.contains("external systems")) {
      return Section.EXTERNAL_SYSTEMS;
    }
    if (normalized.contains("queries") || normalized.contains("views")) {
      return Section.QUERIES;
    }
    if (normalized.contains("risks") || normalized.contains("hot spots")) {
      return Section.RISKS;
    }
    return Section.OTHER;
  }

  private boolean isHeading(String line) {
    return line.startsWith("#") || (line.endsWith(":") && line.length() < 80);
  }

  private String capabilityFromStoryTail(String value) {
    String cleaned = cleanName(value);
    int soThat = cleaned.toLowerCase(Locale.ROOT).indexOf(" so that ");
    if (soThat > 0) {
      cleaned = cleaned.substring(0, soThat).trim();
    }
    return titleFromSentence(cleaned);
  }

  private String subjectBeforeVerb(String value) {
    String cleaned = cleanName(value);
    for (String marker :
        List.of(
            " sends ",
            " captures ",
            " reports ",
            " refunds ",
            " emits ",
            " publishes ",
            " prints ",
            " exports ")) {
      int index = cleaned.toLowerCase(Locale.ROOT).indexOf(marker);
      if (index > 0) {
        return cleanName(cleaned.substring(0, index));
      }
    }
    return titleFromSentence(cleaned);
  }

  private String eventNameFromCapability(String capability) {
    String name = titleFromSentence(capability);
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.startsWith("create ")) {
      return cleanName(name.substring(7)) + " Created";
    }
    if (lower.startsWith("submit ")) {
      return cleanName(name.substring(7)) + " Submitted";
    }
    if (lower.startsWith("buy ") || lower.startsWith("purchase ")) {
      return cleanName(name.replaceFirst("(?i)^(buy|purchase)\\s+", "")) + " Purchased";
    }
    if (lower.startsWith("manage ")) {
      return cleanName(name.substring(7)) + " Managed";
    }
    if (lower.startsWith("audit ")) {
      return cleanName(name.substring(6)) + " Audited";
    }
    return name + " Completed";
  }

  private String domainEntityFromCapability(String capability) {
    String cleaned = titleFromSentence(capability);
    String lower = cleaned.toLowerCase(Locale.ROOT);
    for (String prefix :
        List.of(
            "search ",
            "create ",
            "submit ",
            "update ",
            "withdraw ",
            "assign ",
            "record ",
            "decide ",
            "confirm ",
            "upload ",
            "build ",
            "publish ",
            "buy ",
            "purchase ",
            "capture ",
            "issue ",
            "reconcile ",
            "scan ",
            "bookmark ",
            "register ",
            "select ",
            "mark ",
            "manage ",
            "review ",
            "audit ",
            "analyze ",
            "monitor ")) {
      if (lower.startsWith(prefix)) {
        return titleFromSentence(cleaned.substring(prefix.length()));
      }
    }
    return cleaned;
  }

  private boolean looksLikeQueryNeed(String capability) {
    String lower = capability.toLowerCase(Locale.ROOT);
    return lower.contains("search")
        || lower.contains("review")
        || lower.contains("browse")
        || lower.contains("see ")
        || lower.contains("monitor")
        || lower.contains("analyze")
        || lower.contains("audit");
  }

  private String titleFromSentence(String value) {
    String cleaned = cleanName(value);
    if (cleaned.length() <= 80) {
      return cleaned;
    }
    int period = cleaned.indexOf('.');
    if (period > 12 && period < 80) {
      return cleaned.substring(0, period).trim();
    }
    return cleaned.substring(0, 80).trim();
  }

  private String cleanName(String value) {
    if (value == null) {
      return "";
    }
    String spaced = CAMEL.matcher(value).replaceAll(" ");
    return spaced.replaceAll("\\s+", " ").replaceAll("[.;]+$", "").trim();
  }

  private String slug(String value) {
    return cleanName(value)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
  }

  private String truncate(String value, int max) {
    String cleaned = value == null ? "" : value.trim();
    return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
  }

  private enum Section {
    STAKEHOLDERS,
    GOALS,
    STORIES,
    ACCEPTANCE,
    COMMANDS,
    EVENTS,
    POLICIES,
    EXTERNAL_SYSTEMS,
    QUERIES,
    RISKS,
    OTHER
  }

  private record StoryFact(String actor, String capability, String sourceLine) {}
}
