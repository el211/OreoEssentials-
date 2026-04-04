package fr.elias.oreoEssentials.modules.nametag;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persists the set of players who have toggled their nametag OFF.
 * Stored at: plugins/OreoEssentials/custom-nameplates/toggled-off.yml
 */
public final class NametageToggleStore {

    private final File file;
    private final Set<UUID> toggledOff = new HashSet<>();

    public NametageToggleStore(OreoEssentials plugin) {
        File folder = new File(plugin.getDataFolder(), "custom-nameplates");
        folder.mkdirs();
        this.file = new File(folder, "toggled-off.yml");
        load();
    }

    /** Returns true if this player has toggled their nametag OFF. */
    public boolean isToggledOff(UUID uuid) {
        return toggledOff.contains(uuid);
    }

    /**
     * Flips the toggle for this player.
     * @return true if the nametag is now OFF, false if now ON.
     */
    public boolean toggle(UUID uuid) {
        boolean nowOff;
        if (toggledOff.contains(uuid)) {
            toggledOff.remove(uuid);
            nowOff = false;
        } else {
            toggledOff.add(uuid);
            nowOff = true;
        }
        save();
        return nowOff;
    }

    /** Force-sets the toggle state. */
    public void set(UUID uuid, boolean off) {
        if (off) toggledOff.add(uuid);
        else toggledOff.remove(uuid);
        save();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        List<String> list = yml.getStringList("toggled-off");
        for (String s : list) {
            try { toggledOff.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("toggled-off", toggledOff.stream().map(UUID::toString).toList());
        try { yml.save(file); } catch (IOException ignored) {}
    }
}
