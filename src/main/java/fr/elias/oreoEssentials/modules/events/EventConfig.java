// File: src/main/java/fr/elias/oreoEssentials/events/EventConfig.java
package fr.elias.oreoEssentials.modules.events;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class EventConfig {
    private final File file;
    private Map<EventType, List<String>> map = new EnumMap<>(EventType.class);

    public EventConfig(File dataFolder) {
        this.file = new File(dataFolder, "events.yml");
        if (!file.exists()) saveDefault();
        reload();
    }

    public void reload() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        Map<EventType, List<String>> tmp = new EnumMap<>(EventType.class);
        for (EventType t : EventType.values()) {
            List<String> cmds = y.getStringList("events." + t.name());
            if (cmds == null) cmds = Collections.emptyList();
            tmp.put(t, new ArrayList<>(cmds));
        }
        this.map = tmp;
    }

    public List<String> commands(EventType type) {
        return map.getOrDefault(type, Collections.emptyList());
    }

    private void saveDefault() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("events.firstJoinServer", List.of(
                "asConsole! title [playerName] title {\"text\":\"Welcome!\",\"color\":\"green\"}",
                "asConsole! give [playerName] bread 8"
        ));
        y.set("events.joinServer", List.of());
        y.set("events.quitServer", List.of());
        y.set("events.playerKillPlayer", List.of(
                "asConsole! broadcast [killerName] defeated [playerName]"
        ));
        // All others empty by default:
        for (EventType t : EventType.values()) {
            String path = "events." + t.name();
            if (!y.isList(path)) y.set(path, new ArrayList<String>());
        }
        try { y.save(file); } catch (IOException ignored) {}
    }
}
