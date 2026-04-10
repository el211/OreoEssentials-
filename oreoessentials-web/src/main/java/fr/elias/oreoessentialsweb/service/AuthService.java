package fr.elias.oreoessentialsweb.service;

import fr.elias.oreoessentialsweb.dto.auth.*;
import fr.elias.oreoessentialsweb.model.UserRole;
import fr.elias.oreoessentialsweb.model.WebUser;
import fr.elias.oreoessentialsweb.repository.WebUserRepository;
import fr.elias.oreoessentialsweb.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final WebUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse registerPlayer(PlayerRegisterRequest request) {
        validateNewAccount(request.email(), request.username());

        WebUser user = WebUser.builder()
                .username(request.username())
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.PLAYER)
                .minecraftUuid(request.minecraftUuid())
                .minecraftUsername(request.minecraftUsername())
                .build();

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse registerOwner(OwnerRegisterRequest request) {
        validateNewAccount(request.email(), request.username());

        WebUser user = WebUser.builder()
                .username(request.username())
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.OWNER)
                .build();

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.emailOrUsername(), request.password()));

        UserDetails details = (UserDetails) auth.getPrincipal();
        Long userId = Long.parseLong(details.getUsername());
        WebUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return buildAuthResponse(user);
    }

    @Transactional
    public TokenRefreshResponse refreshToken(String rawRefreshToken) {
        // Validate against stored hash (server-side revocation check)
        var stored = refreshTokenService.validate(rawRefreshToken);

        // Also validate the JWT signature / expiry
        if (!tokenProvider.validateToken(rawRefreshToken) || !tokenProvider.isRefreshToken(rawRefreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        WebUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String newAccessToken = tokenProvider.generateAccessToken(user.getId(), user.getRole().name());
        return new TokenRefreshResponse(newAccessToken);
    }

    /** Revokes all refresh tokens for the current user (logout). */
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAll(userId);
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void validateNewAccount(String email, String username) {
        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
    }

    private AuthResponse buildAuthResponse(WebUser user) {
        String accessToken  = tokenProvider.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        // Persist hashed refresh token for server-side revocation
        refreshTokenService.store(user.getId(), refreshToken);

        return new AuthResponse(accessToken, refreshToken, user.getRole().name(), user.getId(), user.getUsername());
    }
}
