package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.service.LayoutService;
import io.mehdieidi.modless.platform.core.service.ModelingConfigService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ModelingController {

    private final ModelingConfigService modelingConfig;
    private final LayoutService layoutService;

    public ModelingController(ModelingConfigService modelingConfig, LayoutService layoutService) {
        this.modelingConfig = modelingConfig;
        this.layoutService = layoutService;
    }

    @GetMapping("/modeling/config")
    Map<String, Object> config() {
        return modelingConfig.config();
    }

    @PostMapping("/layout")
    LayoutService.LayoutResponse layout(@RequestBody LayoutService.LayoutRequest request) {
        return layoutService.layout(request);
    }
}
