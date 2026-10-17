package io.mehdieidi.modriss.platform.psm;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Checks presentation metadata without allowing it to change the uploaded language. */
final class PsmVisualSyntax {
  private PsmVisualSyntax() {}

  static void validate(JsonNode syntax, JsonNode schema) {
    Set<String> types = new HashSet<>();
    Set<String> concrete = new HashSet<>();
    for (JsonNode type : schema) {
      String key = type.path("typeKey").asText(type.path("name").asText());
      types.add(key);
      if (!type.path("abstract").asBoolean()) concrete.add(key);
    }
    if (!concrete.contains(syntax.path("rootType").asText()))
      fail("Choose a concrete root EClass.");
    Set<String> groups = identifiers(syntax.path("paletteGroups"), "palette group");
    identifiers(syntax.path("views"), "view");
    Set<String> defined = new HashSet<>();
    for (JsonNode element : syntax.path("elements")) {
      String type = element.path("type").asText();
      if (!types.contains(type) || !defined.add(type))
        fail("Element notation must name distinct EClasses in this metamodel.");
      if (!groups.contains(element.path("group").asText()))
        fail("Choose an existing palette group for every element.");
      String icon = element.path("icon").asText("placeholder");
      if (!icon.matches("[A-Za-z0-9_-]+")
          && !(icon.startsWith("/assets/icons/")
              && icon.matches("[A-Za-z0-9_./-]+")
              && !icon.contains(".."))) fail("Choose an icon from the application icon library.");
      String role = element.path("role").asText("node");
      if (!Set.of("node", "container", "detail", "support").contains(role))
        fail("Unknown visual role.");
      for (String theme : Set.of("light", "dark")) {
        if (element.path("color").has(theme)
            && !element.path("color").path(theme).asText().matches("#[0-9a-fA-F]{6}"))
          fail("Use six-digit hex colors.");
      }
    }
    for (JsonNode view : syntax.path("views")) {
      if (!view.path("types").isArray()) fail("Every view needs its element types.");
      for (JsonNode type : view.path("types"))
        if (!types.contains(type.asText())) fail("A view names an unknown EClass.");
    }
  }

  private static Set<String> identifiers(JsonNode entries, String label) {
    if (!entries.isArray() || entries.isEmpty() || entries.size() > 100)
      fail("Supply between 1 and 100 " + label + "s.");
    Set<String> ids = new HashSet<>();
    for (JsonNode entry : entries) {
      String id = entry.path("id").asText();
      if (!id.matches("[A-Za-z0-9_-]{1,100}")
          || !ids.add(id)
          || entry.path("name").asText().isBlank())
        fail("Every " + label + " needs a unique id and a name.");
    }
    return ids;
  }

  private static void fail(String message) {
    throw new PlatformException(400, message);
  }
}
