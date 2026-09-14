package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.model.application.StoredViewLayoutService;
import io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.modriss.platform.modeling.layout.LayoutService;
import io.mehdieidi.modriss.platform.modeling.methodology.ModelingProcessService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes modeling configuration and diagram layout operations. */
@RestController
@RequestMapping("/api")
public class ModelingController {

  private final ModelingConfigService modelingConfig;
  private final ModelingProcessService modelingProcess;
  private final LayoutService layoutService;
  private final StoredViewLayoutService storedViewLayouts;
  private final AuthSupport auth;

  /**
   * Creates the modeling controller.
   *
   * @param modelingConfig modeling palette and configuration service
   * @param modelingProcess modeling methodology process service
   * @param layoutService stateless diagram layout service
   * @param storedViewLayouts stored-view layout service
   * @param auth controller authentication support
   */
  public ModelingController(
      ModelingConfigService modelingConfig,
      ModelingProcessService modelingProcess,
      LayoutService layoutService,
      StoredViewLayoutService storedViewLayouts,
      AuthSupport auth) {
    this.modelingConfig = modelingConfig;
    this.modelingProcess = modelingProcess;
    this.layoutService = layoutService;
    this.storedViewLayouts = storedViewLayouts;
    this.auth = auth;
  }

  /**
   * Returns the frontend modeling palette and related configuration.
   *
   * @return modeling configuration
   */
  @GetMapping("/modeling/config")
  Map<String, Object> config() {
    return modelingConfig.config();
  }

  /**
   * Returns the canonical modeling process definition for a level.
   *
   * @param level {@code cim}, {@code pim}, {@code psm}, {@code artifact}, or {@code end-to-end}
   * @return SPEM-aligned process definition
   */
  @GetMapping("/modeling/process/{level:cim|pim|psm|artifact|end-to-end}")
  Map<String, Object> processDefinition(@PathVariable String level) {
    return modelingProcess.processDefinition(level);
  }

  /**
   * Returns concept coverage matrix for a modeling level.
   *
   * @param level {@code cim}, {@code pim}, or {@code psm}
   * @return coverage matrix
   */
  @GetMapping("/modeling/process/{level:cim|pim|psm}/coverage")
  Map<String, Object> processCoverageMatrix(@PathVariable String level) {
    return modelingProcess.coverageMatrix(level);
  }

  /**
   * Computes positions for an ad hoc diagram.
   *
   * @param request diagram layout request
   * @return computed node positions
   */
  @PostMapping("/layout")
  LayoutService.LayoutResponse layout(@RequestBody LayoutService.LayoutRequest request) {
    return layoutService.layout(request);
  }

  /**
   * Computes and persists layout positions for a stored model view.
   *
   * @param token session token
   * @param level model level API name
   * @param modelId model identifier
   * @param viewId view identifier
   * @param force whether to replace existing positions
   * @param strategy optional layout strategy
   * @return updated stored-view layout
   */
  @PostMapping("/{level:cim|pim|psm}/{modelId}/views/{viewId}/layout")
  StoredViewLayoutService.StoredViewLayoutResponse layoutStoredView(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String level,
      @PathVariable String modelId,
      @PathVariable String viewId,
      @RequestParam(defaultValue = "false") boolean force,
      @RequestParam(required = false) String strategy) {
    return storedViewLayouts.layout(
        auth.user(token), ModelLevel.fromApiName(level), modelId, viewId, force, strategy);
  }
}
