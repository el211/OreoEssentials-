package fr.elias.oreoessentialsweb.controller;

import fr.elias.oreoessentialsweb.model.WebUser;
import fr.elias.oreoessentialsweb.repository.ServerRepository;
import fr.elias.oreoessentialsweb.repository.WebUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Platform-level administration endpoints.
 * Accessible only to users with role ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final WebUserRepository userRepository;
    private final ServerRepository serverRepository;

    /** List all users (paginated). */
    @GetMapping("/users")
    public ResponseEntity<Page<WebUser>> listUsers(
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(userRepository.findAll(pageable));
    }

    /** Get a specific user by ID. */
    @GetMapping("/users/{id}")
    public ResponseEntity<WebUser> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Deactivate a user account. */
    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        WebUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setActive(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    /** Reactivate a user account. */
    @PostMapping("/users/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long id) {
        WebUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setActive(true);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    /** Deactivate a registered server. */
    @PostMapping("/servers/{id}/deactivate")
    public ResponseEntity<Void> deactivateServer(@PathVariable Long id) {
        serverRepository.findById(id).ifPresent(s -> {
            s.setActive(false);
            serverRepository.save(s);
        });
        return ResponseEntity.noContent().build();
    }

    /** Platform stats. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers",   userRepository.count(),
                "totalServers", serverRepository.count()
        ));
    }
}
