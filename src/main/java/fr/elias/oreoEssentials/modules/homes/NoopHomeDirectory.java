package fr.elias.oreoEssentials.modules.homes;

import fr.elias.oreoEssentials.modules.homes.home.HomeDirectory;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class NoopHomeDirectory implements HomeDirectory {
    @Override public void setHomeServer(UUID uuid, String name, String server) {}
    @Override public String getHomeServer(UUID uuid, String name) { return null; }
    @Override public void deleteHome(UUID uuid, String name) {}
    @Override public Set<String> listHomes(UUID uuid) { return Collections.emptySet(); }
}