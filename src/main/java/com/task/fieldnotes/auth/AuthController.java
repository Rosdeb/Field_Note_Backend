package com.task.fieldnotes.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login, JWT token refresh, profile, and logout")
public class AuthController {
    public record Credentials(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(min = 10, max = 72) String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account and returns an initial session with JWT access token and refresh token.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.Session register(@Valid @RequestBody Credentials input) {
        return auth.register(input.email(), input.password());
    }

    @Operation(summary = "Log in with credentials", description = "Authenticates user with email and password, returning JWT access token and refresh token.")
    @PostMapping("/login")
    public AuthService.Session login(@Valid @RequestBody Credentials input) {
        return auth.login(input.email(), input.password());
    }

    @Operation(summary = "Refresh JWT access token", description = "Refreshes an expired or active JWT access token using a valid refresh token with token rotation.")
    @PostMapping("/refresh")
    public AuthService.Session refresh(@Valid @RequestBody RefreshRequest input) {
        return auth.refresh(input.refreshToken());
    }

    @Operation(summary = "Get current authenticated user", description = "Returns the user ID and email of the currently authenticated bearer token.")
    @GetMapping("/me")
    public AuthService.User me(@AuthenticationPrincipal AuthService.User user) {
        return user;
    }

    @Operation(summary = "Log out", description = "Revokes the active JWT bearer token and/or refresh token.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication, @RequestBody(required = false) RefreshRequest request) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            auth.logoutByRefreshToken(request.refreshToken());
        }
        if (authentication != null && authentication.getCredentials() instanceof String token) {
            auth.logout(token);
        }
    }
}
