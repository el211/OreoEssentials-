package fr.elias.oreoessentialsweb.controller;

import fr.elias.oreoessentialsweb.dto.server.*;
import fr.elias.oreoessentialsweb.service.ServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    /** Public: get basic info about a server by slug (for player linking UI). */
    @GetMapping("/public/{slug}")
    public ResponseEntity<ServerResponse> getPublic(@PathVariable String slug) {
        return ResponseEntity.ok(serverService.getServerBySlug(slug));
    }

    /** Owner: list all servers they own. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<List<ServerResponse>> myServers(
            @AuthenticationPrincipal UserDetails principal) {
        Long ownerId = Long.parseLong(principal.getUsername());
        return ResponseEntity.ok(serverService.getOwnerServers(ownerId));
    }

    /** Owner: create a new server registration. */
    @PostMapping
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<ServerCreateResponse> create(
            @Valid @RequestBody ServerCreateRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Long ownerId = Long.parseLong(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serverService.createServer(ownerId, request));
    }

    /** Owner: regenerate the plugin API key. Returns the new raw key once. */
    @PostMapping("/{serverId}/regenerate-key")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> regenerateKey(
            @PathVariable Long serverId,
            @AuthenticationPrincipal UserDetails principal) {
        Long ownerId = Long.parseLong(principal.getUsername());
        String newKey = serverService.regenerateApiKey(serverId, ownerId);
        return ResponseEntity.ok(Map.of(
                "apiKey", newKey,
                "message", "New API key generated. Update your plugin config immediately."));
    }

    /** Owner: update the server's MongoDB URI (re-encrypted automatically). */
    @PutMapping("/{serverId}/mongo-uri")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<Void> updateMongoUri(
            @PathVariable Long serverId,
            @RequestBody UpdateMongoUriRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Long ownerId = Long.parseLong(principal.getUsername());
        serverService.updateMongoUri(serverId, ownerId, request.mongoUri(), request.databaseName());
        return ResponseEntity.noContent().build();
    }
}
