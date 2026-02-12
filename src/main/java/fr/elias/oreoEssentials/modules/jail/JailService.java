package fr.elias.oreoEssentials.modules.jail;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.TimeText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class JailService {
    private final Plugin plugin;
    private final JailStorage storage;

    private final Map<String, JailModels.Jail> jails = new HashMap<>();
    private final Map<UUID, JailModels.Sentence> active = new HashMap<>();
    private BukkitTask guardTask;

    private final Set<String> blockedCommands = new HashSet<>(Arrays.asList(
            "spawn", "home", "sethome", "warp", "rtp", "tpa", "tp", "back",
            "tpahere", "tpaccept", "wild", "randomtp"
    ));

    public JailService(Plugin plugin, JailStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void enable() {
        jails.clear();
        jails.putAll(storage.loadJails());
        active.clear();
        active.putAll(storage.loadSentences());

        if (guardTask != null) guardTask.cancel();
        guardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("[Jails] Loaded " + jails.size() + " jails, " + active.size() + " active sentence(s).");
    }

    public void disable() {
        try {
            storage.saveJails(jails);
        } catch (Throwable ignored) {}

        try {
            storage.saveSentences(active);
        } catch (Throwable ignored) {}

        if (guardTask != null) {
            guardTask.cancel();
            guardTask = null;
        }

        storage.close();
    }


    public boolean createOrUpdateJail(String name, JailModels.Cuboid region, String world) {
        name = name.toLowerCase(Locale.ROOT);
        JailModels.Jail j = jails.getOrDefault(name, new JailModels.Jail());
        j.name = name;
        j.world = world;
        j.region = region;
        jails.put(name, j);

        try {
            storage.saveJail(j);
        } catch (UnsupportedOperationException e) {
            // Fallback to bulk save for YAML
            storage.saveJails(jails);
        }

        return true;
    }

    public boolean addCell(String jailName, String cellId, Location loc) {
        JailModels.Jail j = jails.get(jailName.toLowerCase(Locale.ROOT));
        if (j == null) return false;

        j.cells.put(cellId, loc.clone());

        try {
            storage.saveJail(j);
        } catch (UnsupportedOperationException e) {
            // Fallback to bulk save for YAML
            storage.saveJails(jails);
        }

        return true;
    }

    public boolean deleteJail(String name) {
        name = name.toLowerCase(Locale.ROOT);
        JailModels.Jail removed = jails.remove(name);

        if (removed != null) {
            try {
                storage.deleteJail(name);
            } catch (UnsupportedOperationException e) {
                storage.saveJails(jails);
            }
            return true;
        }

        return false;
    }

    public Map<String, JailModels.Jail> allJails() {
        return Collections.unmodifiableMap(jails);
    }

    public JailModels.Jail getJail(String name) {
        return jails.get(name.toLowerCase(Locale.ROOT));
    }


    /**
     * Jail a player (works even if they're offline).
     * They will be teleported on next login if offline.
     */
    public boolean jail(UUID player, String jailName, String cellId,
                        long durationMs, String reason, String by) {
        jailName = jailName.toLowerCase(Locale.ROOT);
        JailModels.Jail j = jails.get(jailName);

        if (j == null || !j.isValid()) {
            return false;
        }

        Location spawn = (cellId != null ? j.cells.get(cellId) : null);
        if (spawn == null && !j.cells.isEmpty()) {
            spawn = j.cells.values().iterator().next();
        }
        if (spawn == null) {
            return false;
        }

        JailModels.Sentence s = new JailModels.Sentence();
        s.player = player;
        s.jailName = jailName;
        s.cellId = cellId;
        s.reason = reason == null ? "" : reason;
        s.by = by == null ? "console" : by;
        s.endEpochMs = durationMs <= 0 ? 0 : (System.currentTimeMillis() + durationMs);

        active.put(player, s);

        try {
            storage.saveSentence(s);
        } catch (UnsupportedOperationException e) {
            storage.saveSentences(active);
        }

        Player p = Bukkit.getPlayer(player);
        if (p != null && p.isOnline()) {
            final Location finalSpawn = spawn;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.teleport(finalSpawn);
                p.sendMessage("§cYou have been jailed"
                        + (s.endEpochMs > 0 ? (" for " + TimeText.format(durationMs)) : " permanently")
                        + (s.reason.isBlank() ? "" : " §7Reason: §f" + s.reason));
            });
        }

        try {
            if (plugin instanceof OreoEssentials oe) {
                var d = oe.getDiscordMod();
                if (d != null && d.isEnabled()) {
                    String name = String.valueOf(Bukkit.getOfflinePlayer(player).getName());
                    d.notifyJail(name, player, j.name, cellId, s.reason, s.by, s.endEpochMs);
                }
            }
        } catch (Throwable ignored) {}

        return true;
    }

    public boolean release(UUID player) {
        JailModels.Sentence s = active.remove(player);

        if (s != null) {
            try {
                storage.deleteSentence(player);
            } catch (UnsupportedOperationException e) {
                storage.saveSentences(active);
            }

            Player p = Bukkit.getPlayer(player);
            if (p != null && p.isOnline()) {
                p.sendMessage("§aYou have been released from jail.");
            }

            try {
                if (plugin instanceof OreoEssentials oe) {
                    var d = oe.getDiscordMod();
                    if (d != null && d.isEnabled()) {
                        String name = String.valueOf(Bukkit.getOfflinePlayer(player).getName());
                        d.notifyUnjail(name, player, s.by == null || s.by.isBlank() ? "system" : s.by);
                    }
                }
            } catch (Throwable ignored) {}

            return true;
        }

        return false;
    }

    /**
     * Extend a player's jail sentence.
     * @return true if extended successfully
     */
    public boolean extendSentence(UUID player, long additionalMs, String by) {
        JailModels.Sentence s = active.get(player);
        if (s == null) return false;

        if (s.endEpochMs > 0) {
            s.endEpochMs += additionalMs;
        } else {
            s.endEpochMs = System.currentTimeMillis() + additionalMs;
        }

        s.by = by == null ? "console" : by;

        try {
            storage.saveSentence(s);
        } catch (UnsupportedOperationException e) {
            storage.saveSentences(active);
        }

        Player p = Bukkit.getPlayer(player);
        if (p != null && p.isOnline()) {
            p.sendMessage("§cYour sentence has been extended by " + TimeText.format(additionalMs));
        }

        return true;
    }

    public JailModels.Sentence sentence(UUID u) {
        return active.get(u);
    }

    public boolean isJailed(UUID player) {
        return active.containsKey(player);
    }


    private void tick() {
        boolean changed = false;

        Iterator<Map.Entry<UUID, JailModels.Sentence>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, JailModels.Sentence> e = it.next();
            if (e.getValue().expired()) {
                UUID playerId = e.getKey();
                Player p = Bukkit.getPlayer(playerId);

                if (p != null && p.isOnline()) {
                    p.sendMessage("§aYour jail time is over.");
                }

                it.remove();

                try {
                    storage.deleteSentence(playerId);
                } catch (UnsupportedOperationException ex) {
                    changed = true;
                }
            }
        }


        if (changed) {
            try {
                storage.saveSentences(active);
            } catch (Throwable ignored) {}
        }

        for (Map.Entry<UUID, JailModels.Sentence> e : active.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) continue;

            JailModels.Jail j = jails.get(e.getValue().jailName);
            if (j == null || !j.isValid()) continue;

            if (!p.getWorld().getName().equalsIgnoreCase(j.world)
                    || !j.region.contains(p.getLocation())) {

                Location spawn = j.cells.get(e.getValue().cellId);
                if (spawn == null && !j.cells.isEmpty()) {
                    spawn = j.cells.values().iterator().next();
                }

                if (spawn != null) {
                    final Location finalSpawn = spawn;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.teleport(finalSpawn);
                        p.sendMessage("§cYou cannot escape from jail!");
                    });
                }
            }
        }
    }

    /**
     * Teleport jailed player back to their cell.
     * Used by PlayerJoinEvent listener.
     */
    public void teleportToCell(UUID player) {
        JailModels.Sentence s = active.get(player);
        if (s == null) return;

        JailModels.Jail jail = jails.get(s.jailName);
        if (jail == null || !jail.isValid()) return;

        Location spawn = jail.cells.get(s.cellId);
        if (spawn == null && !jail.cells.isEmpty()) {
            spawn = jail.cells.values().iterator().next();
        }

        if (spawn != null) {
            Player p = Bukkit.getPlayer(player);
            if (p != null && p.isOnline()) {
                final Location finalSpawn = spawn;
                // Delay to ensure world is loaded
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    p.teleport(finalSpawn);

                    long remaining = s.remainingMs();
                    if (remaining > 0) {
                        p.sendMessage("§cYou are still jailed for " + TimeText.format(remaining));
                    } else if (s.endEpochMs == 0) {
                        p.sendMessage("§cYou are permanently jailed.");
                    }
                }, 5L);
            }
        }
    }

    public boolean isCommandBlockedFor(Player p, String baseCmd) {
        JailModels.Sentence s = active.get(p.getUniqueId());
        return s != null && blockedCommands.contains(baseCmd.toLowerCase(Locale.ROOT));
    }

    public Set<String> getBlockedCommands() {
        return Collections.unmodifiableSet(blockedCommands);
    }
}