// File: src/main/java/fr/elias/oreoEssentials/services/VanishService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishService {

    private final Plugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishService(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    public boolean toggle(Player p) {
        if (isVanished(p)) {
            show(p);
            vanished.remove(p.getUniqueId());
            return false;
        } else {
            hide(p);
            vanished.add(p.getUniqueId());
            return true;
        }
    }

    public void hide(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.hidePlayer(plugin, p);
        }
    }

    public void show(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.showPlayer(plugin, p); // <--- plugin REQUIRED
        }
    }

    public void applyToJoiner(Player joiner) {
        for (UUID id : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(id);
            if (vanishedPlayer != null && vanishedPlayer.isOnline()) {
                joiner.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    public void handleQuit(Player quitter) {
        vanished.remove(quitter.getUniqueId());
    }
}
