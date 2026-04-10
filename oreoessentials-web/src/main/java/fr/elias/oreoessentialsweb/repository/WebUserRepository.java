package fr.elias.oreoessentialsweb.repository;

import fr.elias.oreoessentialsweb.model.WebUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebUserRepository extends JpaRepository<WebUser, Long> {

    Optional<WebUser> findByEmail(String email);

    Optional<WebUser> findByUsername(String username);

    Optional<WebUser> findByMinecraftUuid(UUID minecraftUuid);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
