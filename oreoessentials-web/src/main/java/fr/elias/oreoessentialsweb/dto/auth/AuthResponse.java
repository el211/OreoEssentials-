package fr.elias.oreoessentialsweb.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,   // store in HttpOnly cookie on the frontend
        String role,
        Long userId,
        String username
) {}
