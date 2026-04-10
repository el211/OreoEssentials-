package fr.elias.oreoessentialsweb.controller;

import fr.elias.oreoessentialsweb.dto.plugin.PluginSyncRequest;
import fr.elias.oreoessentialsweb.dto.plugin.PluginSyncResponse;
import fr.elias.oreoessentialsweb.security.ApiKeyAuthFilter;
import fr.elias.oreoessentialsweb.service.PlayerDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API consumed by the OreoEssentials Minecraft plugin.
 *
 * All endpoints require a valid server API key in the X-Api-Key header.
 * The key is authenticated by {@link ApiKeyAuthFilter} before reaching here.
 *
 * Plugin config.yml example:
 *   web-panel:
 *     enabled: true
 *     url: https://your-panel.example.com
 *     api-key: oreo_abc123_xyz789...
 *     sync-interval-ticks: 6000   # every 5 minutes
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/plugin")
@PreAuthorize("hasRole('PLUGIN')")
@RequiredArgsConstructor
public class PluginApiController {

    private final PlayerDataService playerDataService;

    /**
     * Sync a single player's data snapshot.
     * Called by the plugin on login, logout, and periodically.
     */
    @PostMapping("/sync")
    public ResponseEntity<PluginSyncResponse> syncPlayer(
            @Valid @RequestBody PluginSyncRequest request,
            @AuthenticationPrincipal ApiKeyAuthFilter.PluginPrincipal principal) {

        playerDataService.ingestPlayerSync(
                principal.serverId(),
                request.playerUuid(),
                request.playerDataJson()
        );

        log.debug("Accepted sync for player {} on server {}", request.playerName(), principal.serverId());
        return ResponseEntity.ok(new PluginSyncResponse(true, "Sync accepted"));
    }

    /**
     * Bulk sync — accepts multiple players in a single request.
     * Useful for syncing all online players on plugin enable or periodically.
     */
    @PostMapping("/sync/bulk")
    public ResponseEntity<PluginSyncResponse> syncBulk(
            @Valid @RequestBody List<PluginSyncRequest> requests,
            @AuthenticationPrincipal ApiKeyAuthFilter.PluginPrincipal principal) {

        for (PluginSyncRequest req : requests) {
            playerDataService.ingestPlayerSync(principal.serverId(), req.playerUuid(), req.playerDataJson());
        }

        log.debug("Accepted bulk sync of {} players on server {}", requests.size(), principal.serverId());
        return ResponseEntity.ok(new PluginSyncResponse(true, "Bulk sync accepted: " + requests.size() + " players"));
    }

    /**
     * Heartbeat — lets the plugin confirm the panel is reachable.
     * Returns 200 if authenticated.
     */
    @GetMapping("/ping")
    public ResponseEntity<PluginSyncResponse> ping(
            @AuthenticationPrincipal ApiKeyAuthFilter.PluginPrincipal principal) {
        return ResponseEntity.ok(new PluginSyncResponse(true, "pong from server " + principal.serverId()));
    }
}
