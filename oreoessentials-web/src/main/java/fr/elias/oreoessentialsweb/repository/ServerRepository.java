package fr.elias.oreoessentialsweb.repository;

import fr.elias.oreoessentialsweb.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {

    Optional<Server> findBySlug(String slug);

    List<Server> findAllByOwnerId(Long ownerId);

    boolean existsBySlug(String slug);

    /** Used by API key lookup: find candidate servers by key prefix for fast filtering. */
    List<Server> findAllByApiKeyPrefix(String apiKeyPrefix);
}
