package fr.elias.oreoEssentials.modules.homes.home;

import java.util.Set;
import java.util.UUID;

public interface HomeDirectory {
    void setHomeServer(UUID uuid, String name, String server);
    String getHomeServer(UUID uuid, String name);
    void deleteHome(UUID uuid, String name);
    Set<String> listHomes(UUID uuid);  // Add this line
}