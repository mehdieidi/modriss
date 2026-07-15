package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.backend.admin.AdminThemeService;
import io.mehdieidi.varka.backend.admin.AdminThemeService.ThemeState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the active frontend theme profile. */
@RestController
@RequestMapping("/api/theme")
public class ThemeController {

  private final AdminThemeService themes;

  public ThemeController(AdminThemeService themes) {
    this.themes = themes;
  }

  @GetMapping
  ThemeState currentTheme(@RequestParam(defaultValue = "dark") String scheme) {
    return themes.currentTheme(scheme);
  }
}
