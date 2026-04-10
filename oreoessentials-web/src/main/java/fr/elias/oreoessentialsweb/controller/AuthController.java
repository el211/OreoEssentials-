package fr.elias.oreoessentialsweb.controller;

import fr.elias.oreoessentialsweb.dto.auth.*;
import fr.elias.oreoessentialsweb.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Register a new player account. */
    @PostMapping("/register/player")
    public ResponseEntity<AuthResponse> registerPlayer(
            @Valid @RequestBody PlayerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerPlayer(request));
    }

    /** Register a new server-owner account. */
    @PostMapping("/register/owner")
    public ResponseEntity<AuthResponse> registerOwner(
            @Valid @RequestBody OwnerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerOwner(request));
    }

    /** Login with email/username + password. Returns access + refresh tokens. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Exchange a valid refresh token for a new access token.
     * The refresh token is verified both by JWT signature and by its stored hash.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
    }

    /**
     * Logout — revokes all refresh tokens for the current user.
     * The client should also discard its stored access and refresh tokens.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails principal) {
        Long userId = Long.parseLong(principal.getUsername());
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
