package io.mehdieidi.modless.platform.assistant.delta;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Resolves PIM deployable placement under {@code ServerlessService} val containment. */
public final class PimDeployablePlacement {

  public static final String DEFAULT_SERVICE_LOCAL_ID = "__assistant_default_service__";

  private static final Set<String> LEGACY_ROOT_DEPLOYABLE_COLLECTIONS =
      Set.of(
          "functions",
          "apis",
          "channels",
          "schedules",
          "stores",
          "workflows",
          "adapters",
          "datastores",
          "objectstores",
          "externaladapters");

  private PimDeployablePlacement() {}

  public static boolean isLegacyRootDeployableCollection(String ownerType, String referenceName) {
    return "PIMModel".equals(ownerType)
        && referenceName != null
        && LEGACY_ROOT_DEPLOYABLE_COLLECTIONS.contains(
            referenceName.trim().toLowerCase(Locale.ROOT));
  }

  public static Optional<String> serviceContainmentFeature(
      AssistantMetamodelSchemaService schemas, String childType) {
    List<AssistantMetamodelSchemaService.ReferenceSchema> candidates =
        schemas.containments(ModelLevel.PIM, "ServerlessService", childType);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0).name());
  }

  public static Optional<ModelDelta.Placement> servicePlacement(
      AssistantMetamodelSchemaService schemas, String childType, String serviceLocalId) {
    return serviceContainmentFeature(schemas, childType)
        .map(feature -> new ModelDelta.Placement(serviceLocalId, feature));
  }
}
