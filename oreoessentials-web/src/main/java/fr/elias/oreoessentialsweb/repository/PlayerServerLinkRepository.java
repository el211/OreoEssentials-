package fr.elias.oreoessentialsweb.repository;

import fr.elias.oreoessentialsweb.model.PlayerServerLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerServerLinkRepository extends JpaRepository<PlayerServerLink, Long> {

    List<PlayerServerLink> findAllByPlayerId(Long playerId);

    List<PlayerServerLink> findAllByServerId(Long serverId);

    Optional<PlayerServerLink> findByPlayerIdAndServerId(Long playerId, Long serverId);

    Optional<PlayerServerLink> findByServerIdAndMinecraftUuid(Long serverId, UUID minecraftUuid);

    boolean existsByPlayerIdAndServerId(Long playerId, Long serverId);
}
