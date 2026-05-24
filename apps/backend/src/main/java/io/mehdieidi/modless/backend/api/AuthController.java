package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthResult result = authService.register(request.email(), request.password(),
                request.displayName());
        return new AuthResponse(result.token(), UserDto.from(result.user()));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request.email(), request.password());
        return new AuthResponse(result.token(), UserDto.from(result.user()));
    }

    @GetMapping("/me")
    UserDto me(@RequestHeader("X-Auth-Token") String token) {
        return UserDto.from(authService.requireUser(token));
    }

    @PutMapping("/me")
    UserDto updateMe(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody UpdateMeRequest request) {
        return UserDto.from(authService.updateDisplayName(token, request.displayName()));
    }

    @PostMapping("/logout")
    void logout(@RequestHeader("X-Auth-Token") String token) {
        authService.logout(token);
    }

    public record RegisterRequest(@Email @NotBlank String email, @Size(min = 8) String password,
                                  @NotBlank @Size(max = 80) String displayName) {

    }

    public record LoginRequest(@Email @NotBlank String email, @Size(min = 8) String password) {

    }

    public record UpdateMeRequest(@NotBlank @Size(max = 80) String displayName) {

    }

    public record AuthResponse(String token, UserDto user) {

    }

    public record UserDto(String id, String email, String displayName) {

        static UserDto from(UserRecord user) {
            return new UserDto(user.id(), user.email(), user.displayName());
        }
    }
}
