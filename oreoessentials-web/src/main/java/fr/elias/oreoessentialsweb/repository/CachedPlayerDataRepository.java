package fr.elias.oreoessentialsweb.repository;

import fr.elias.oreoessentialsweb.model.CachedPlayerData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CachedPlayerDataRepository extends JpaRepository<CachedPlayerData, Long> {

    Optional<CachedPlayerData> findByLinkId(Long linkId);
}
