package fr.elias.oreoessentialsweb.repository;

import fr.elias.oreoessentialsweb.model.ServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServerConfigRepository extends JpaRepository<ServerConfig, Long> {

    Optional<ServerConfig> findByServerId(Long serverId);
}
