package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.PlatformException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FutureFeatureController {

    @RequestMapping({"/api/github/**", "/api/impact/**", "/api/admin/**"})
    void future() {
        throw new PlatformException(501, "This feature is planned for a future backend iteration.");
    }
}
