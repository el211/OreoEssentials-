package fr.elias.oreoEssentials.modules.playerwarp;

import java.util.List;
import java.util.UUID;

public interface PlayerWarpStorage {

    void save(PlayerWarp warp);

    PlayerWarp getById(String id);

    PlayerWarp getByOwnerAndName(UUID owner, String nameLower);

    boolean delete(String id);

    List<PlayerWarp> listByOwner(UUID owner);

    List<PlayerWarp> listAll();

}
