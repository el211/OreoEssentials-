package fr.elias.oreoessentialsweb.controller;

import fr.elias.oreoessentialsweb.dto.player.LinkServerRequest;
import fr.elias.oreoessentialsweb.dto.player.PlayerDataResponse;
import fr.elias.oreoessentialsweb.model.PlayerServerLink;
import fr.elias.oreoessentialsweb.repository.ServerRepository;
import fr.elias.oreoessentialsweb.service.PlayerDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerDataService playerDataService;
    private final ServerRepository serverRepository;

    /**
     * Link the authenticated player to a server by slug + Minecraft UUID.
     * The link starts unverified. Verification happens when the plugin pushes
     * data for that UUID (or when a direct Mongo lookup confirms the UUID exists).
     */
    @PostMapping("/me/links")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<String> linkToServer(
            @Valid @RequestBody LinkServerRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long playerId = Long.parseLong(principal.getUsername());
        Long serverId = serverRepository.findBySlug(request.serverSlug())
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + request.serverSlug()))
                .getId();

        PlayerServerLink link = playerDataService.linkPlayer(playerId, serverId, request.minecraftUuid());
        return ResponseEntity.ok("Linked to server. UUID: " + link.getMinecraftUuid()
                + ". Verification will happen automatically when the plugin syncs your data.");
    }

    /**
     * Fetch this player's data for a specific server they are linked to.
     * The server is identified by its slug.
     */
    @GetMapping("/me/data/{serverSlug}")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<PlayerDataResponse> getMyData(
            @PathVariable String serverSlug,
            @AuthenticationPrincipal UserDetails principal) {

        Long playerId = Long.parseLong(principal.getUsername());
        Long serverId = serverRepository.findBySlug(serverSlug)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverSlug))
                .getId();

        PlayerDataResponse data = playerDataService.getPlayerData(playerId, serverId);
        return ResponseEntity.ok(data);
    }
}
