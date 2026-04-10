package fr.elias.oreoessentialsweb.security;

import fr.elias.oreoessentialsweb.model.Server;
import fr.elias.oreoessentialsweb.repository.ServerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates Minecraft plugin requests via API key.
 *
 * The plugin sends:  X-Api-Key: oreo_<prefix>_<secret>
 *
 * We extract the prefix, find candidate servers, then BCrypt-verify the full
 * key against the stored hash. Only applies to /api/v1/plugin/** endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String API_KEY_PREFIX_MARKER = "oreo_";
    private static final String PLUGIN_PATH_PREFIX = "/api/v1/plugin/";

    private final ServerRepository serverRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PLUGIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);

        if (!StringUtils.hasText(rawKey) || !rawKey.startsWith(API_KEY_PREFIX_MARKER)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or malformed API key");
            return;
        }

        // Format: oreo_<prefix>_<secret>
        // We use prefix to find candidates, then verify full key hash
        String[] parts = rawKey.split("_", 3);
        if (parts.length != 3) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Malformed API key format");
            return;
        }

        String keyPrefix = parts[1];
        List<Server> candidates = serverRepository.findAllByApiKeyPrefix(keyPrefix);

        Server authenticatedServer = candidates.stream()
                .filter(s -> s.getApiKeyHash() != null && passwordEncoder.matches(rawKey, s.getApiKeyHash()))
                .findFirst()
                .orElse(null);

        if (authenticatedServer == null) {
            log.warn("Failed plugin API key authentication from {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        // Store the authenticated server ID as principal for downstream use
        PluginPrincipal principal = new PluginPrincipal(authenticatedServer.getId());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_PLUGIN")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    /** Simple principal carrying the authenticated server's ID. */
    public record PluginPrincipal(Long serverId) {}
}
