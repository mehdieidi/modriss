package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.service.AuthService;
import org.springframework.stereotype.Component;

@Component
public class AuthSupport {

    private final AuthService authService;

    public AuthSupport(AuthService authService) {
        this.authService = authService;
    }

    public UserRecord user(String token) {
        return authService.requireUser(token);
    }
}
