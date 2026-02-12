package fr.elias.oreoEssentials.modules.playtime;


import java.util.Map;
import java.util.Set;
import java.util.UUID;


public interface PlaytimeDataStore {
    Set<String> getClaimedOnce(UUID uuid);
    Map<String,Integer> getPaidCounts(UUID uuid); // for repeating rewards
    void setClaimedOnce(UUID uuid, Set<String> ids);
    void setPaidCounts(UUID uuid, Map<String,Integer> counts);
    void saveAsync();
}