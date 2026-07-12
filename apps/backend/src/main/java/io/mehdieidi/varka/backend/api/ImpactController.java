package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.backend.impact.ImpactAnalysisService;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Provides traceability and change-impact analysis across models and generated artifacts. */
@RestController
public class ImpactController {

  private final ImpactAnalysisService impact;
  private final AuthSupport auth;

  public ImpactController(ImpactAnalysisService impact, AuthSupport auth) {
    this.impact = impact;
    this.auth = auth;
  }

  @GetMapping("/api/impact/{level:cim|pim|psm}/{modelId}/element/{elementId}")
  ImpactAnalysisService.ElementImpactResponse elementImpact(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @PathVariable("modelId") String modelId,
      @PathVariable("elementId") String elementId) {
    return impact.elementImpact(
        auth.user(token), ModelLevel.fromApiName(level), modelId, elementId);
  }

  @GetMapping("/api/impact/artifact/{artifactId}")
  ImpactAnalysisService.ArtifactImpactResponse artifactImpact(
      @RequestHeader("X-Auth-Token") String token, @PathVariable("artifactId") String artifactId) {
    return impact.artifactImpact(auth.user(token), artifactId);
  }
}
