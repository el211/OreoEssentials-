package fr.elias.oreoessentialsweb.service;

import fr.elias.oreoessentialsweb.model.PlayerServerLink;
import fr.elias.oreoessentialsweb.repository.PlayerServerLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles player-server link verification.
 *
 * A link is considered verified when the plugin pushes sync data for that UUID
 * on the associated server. This confirms the player actually exists on that
 * server and the UUID they claimed is correct.
 *
 * Verification is triggered automatically inside {@link PlayerDataService#ingestPlayerSync}
 * when a matching unverified link is found.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerVerificationService {

    private final PlayerServerLinkRepository linkRepository;

    /**
     * Marks a player–server link as verified.
     * Called when the plugin successfully pushes data for a UUID that matches
     * an existing unverified link on that server.
     */
    @Transactional
    public void verifyLink(Long serverId, UUID minecraftUuid) {
        linkRepository.findByServerIdAndMinecraftUuid(serverId, minecraftUuid)
                .filter(link -> !link.isVerified())
                .ifPresent(link -> {
                    link.setVerified(true);
                    link.setVerifiedAt(Instant.now());
                    linkRepository.save(link);
                    log.info("Verified player-server link: player={} server={} uuid={}",
                            link.getPlayerId(), serverId, minecraftUuid);
                });
    }

    /**
     * Returns whether a specific link is verified.
     */
    @Transactional(readOnly = true)
    public boolean isVerified(Long playerId, Long serverId) {
        return linkRepository.findByPlayerIdAndServerId(playerId, serverId)
                .map(PlayerServerLink::isVerified)
                .orElse(false);
    }
}
