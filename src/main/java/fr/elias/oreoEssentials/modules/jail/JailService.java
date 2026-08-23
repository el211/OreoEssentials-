package fr.elias.oreoEssentials.modules.jail;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.elias.oreoEssentials.util.OreTask;
import fr.elias.oreoEssentials.util.TimeText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class JailService {
    private final Plugin plugin;
    private final JailStorage storage;

    private final Map<String, JailModels.Jail> jails = new ConcurrentHashMap<>();
    private final Map<UUID, JailModels.Sentence> active = new ConcurrentHashMap<>();
    private OreTask guardTask;

    private final Set<String> blockedCommands = Set.of(
            "spawn", "home", "sethome", "warp", "rtp", "tpa", "tp", "back",
            "tpahere", "tpaccept", "wild", "randomtp");

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
        guardTask = OreScheduler.runTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("[Jails] Loaded " + jails.size() + " jails, " + active.size() + " active sentence(s).");
    }

    public void disable() {
        if (guardTask != null) {
            guardTask.cancel();
            guardTask = null;
        }
        try { storage.saveJails(new HashMap<>(jails)); } catch (Throwable ignored) {}
        try { storage.saveSentences(new HashMap<>(active)); } catch (Throwable ignored) {}
        storage.close();
    }

    public boolean createOrUpdateJail(String name, JailModels.Cuboid region, String world) {
        name = name.toLowerCase(Locale.ROOT);
        JailModels.Jail j = jails.getOrDefault(name, new JailModels.Jail());
        j.name = name;
        j.world = world;
        j.region = region;
        jails.put(name, j);
        try { storage.saveJail(j); }
        catch (UnsupportedOperationException e) { storage.saveJails(new HashMap<>(jails)); }
        return true;
    }

    public boolean addCell(String jailName, String cellId, Location loc) {
        JailModels.Jail j = jails.get(jailName.toLowerCase(Locale.ROOT));
        if (j == null) return false;
        j.cells.put(cellId, loc.clone());
        try { storage.saveJail(j); }
        catch (UnsupportedOperationException e) { storage.saveJails(new HashMap<>(jails)); }
        return true;
    }

    public boolean deleteJail(String name) {
        name = name.toLowerCase(Locale.ROOT);
        JailModels.Jail removed = jails.remove(name);
        if (removed == null) return false;
        try { storage.deleteJail(name); }
        catch (UnsupportedOperationException e) { storage.saveJails(new HashMap<>(jails)); }
        return true;
    }

    public Map<String, JailModels.Jail> allJails() { return Collections.unmodifiableMap(jails); }
    public JailModels.Jail getJail(String name) { return jails.get(name.toLowerCase(Locale.ROOT)); }

    public boolean jail(UUID player, String jailName, String cellId,
                        long durationMs, String reason, String by) {
        jailName = jailName.toLowerCase(Locale.ROOT);
        JailModels.Jail j = jails.get(jailName);
        if (j == null || !j.isValid()) return false;

        Location spawn = cellId != null ? j.cells.get(cellId) : null;
        if (spawn == null && !j.cells.isEmpty()) spawn = j.cells.values().iterator().next();
        if (spawn == null) return false;

        JailModels.Sentence s = new JailModels.Sentence();
        s.player = player;
        s.jailName = jailName;
        s.cellId = cellId;
        s.reason = reason == null ? "" : reason;
        s.by = by == null ? "console" : by;
        s.endEpochMs = durationMs <= 0 ? 0 : System.currentTimeMillis() + durationMs;
        active.put(player, s);

        try { storage.saveSentence(s); }
        catch (UnsupportedOperationException e) { storage.saveSentences(new HashMap<>(active)); }

        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            Location finalSpawn = spawn.clone();
            OreScheduler.runForEntity(plugin, p, () -> {
                if (!p.isOnline()) return;
                if (OreScheduler.isFolia()) p.teleportAsync(finalSpawn);
                else p.teleport(finalSpawn);
                p.sendMessage("§cYou have been jailed"
                        + (s.endEpochMs > 0 ? " for " + TimeText.format(durationMs) : " permanently")
                        + (s.reason.isBlank() ? "" : " §7Reason: §f" + s.reason));
            });
        }

        try {
            if (plugin instanceof OreoEssentials oe) {
                var d = oe.getDiscordMod();
                if (d != null && d.isEnabled()) {
                    String playerName = String.valueOf(Bukkit.getOfflinePlayer(player).getName());
                    d.notifyJail(playerName, player, j.name, cellId, s.reason, s.by, s.endEpochMs);
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public boolean release(UUID player) {
        JailModels.Sentence s = active.remove(player);
        if (s == null) return false;

        try { storage.deleteSentence(player); }
        catch (UnsupportedOperationException e) { storage.saveSentences(new HashMap<>(active)); }

        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            OreScheduler.runForEntity(plugin, p, () -> {
                if (p.isOnline()) p.sendMessage("§aYou have been released from jail.");
            });
        }

        try {
            if (plugin instanceof OreoEssentials oe) {
                var d = oe.getDiscordMod();
                if (d != null && d.isEnabled()) {
                    String playerName = String.valueOf(Bukkit.getOfflinePlayer(player).getName());
                    d.notifyUnjail(playerName, player, s.by == null || s.by.isBlank() ? "system" : s.by);
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public boolean extendSentence(UUID player, long additionalMs, String by) {
        JailModels.Sentence s = active.get(player);
        if (s == null) return false;
        synchronized (s) {
            s.endEpochMs = s.endEpochMs > 0 ? s.endEpochMs + additionalMs : System.currentTimeMillis() + additionalMs;
            s.by = by == null ? "console" : by;
        }
        try { storage.saveSentence(s); }
        catch (UnsupportedOperationException e) { storage.saveSentences(new HashMap<>(active)); }

        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            OreScheduler.runForEntity(plugin, p, () -> {
                if (p.isOnline()) p.sendMessage("§cYour sentence has been extended by " + TimeText.format(additionalMs));
            });
        }
        return true;
    }

    public JailModels.Sentence sentence(UUID u) { return active.get(u); }
    public boolean isJailed(UUID player) { return active.containsKey(player); }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, JailModels.Sentence> entry : active.entrySet()) {
            UUID playerId = entry.getKey();
            JailModels.Sentence sentence = entry.getValue();

            if (sentence.expired()) {
                if (!active.remove(playerId, sentence)) continue;
                Player p = Bukkit.getPlayer(playerId);
                if (p != null) {
                    OreScheduler.runForEntity(plugin, p, () -> {
                        if (p.isOnline()) p.sendMessage("§aYour jail time is over.");
                    });
                }
                OreScheduler.runAsync(plugin, () -> {
                    try { storage.deleteSentence(playerId); }
                    catch (UnsupportedOperationException ex) {
                        try { storage.saveSentences(new HashMap<>(active)); } catch (Throwable ignored) {}
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[Jails] Failed removing expired sentence: " + t.getMessage());
                    }
                });
                continue;
            }

            Player p = Bukkit.getPlayer(playerId);
            if (p == null) continue;
            JailModels.Jail jail = jails.get(sentence.jailName);
            if (jail == null || !jail.isValid()) continue;

            Location cell = jail.cells.get(sentence.cellId);
            if (cell == null && !jail.cells.isEmpty()) cell = jail.cells.values().iterator().next();
            final Location finalCell = cell == null ? null : cell.clone();
            if (finalCell == null) continue;

            OreScheduler.runForEntity(plugin, p, () -> {
                if (!p.isOnline() || active.get(playerId) != sentence) return;
                boolean outside = !p.getWorld().getName().equalsIgnoreCase(jail.world)
                        || !jail.region.contains(p.getLocation());
                if (!outside) return;

                if (OreScheduler.isFolia()) p.teleportAsync(finalCell);
                else p.teleport(finalCell);
                p.sendMessage("§cYou cannot escape from jail!");
            });
        }
    }

    public void teleportToCell(UUID player) {
        JailModels.Sentence s = active.get(player);
        if (s == null) return;
        JailModels.Jail jail = jails.get(s.jailName);
        if (jail == null || !jail.isValid()) return;

        Location spawn = jail.cells.get(s.cellId);
        if (spawn == null && !jail.cells.isEmpty()) spawn = jail.cells.values().iterator().next();
        if (spawn == null) return;

        Player p = Bukkit.getPlayer(player);
        if (p == null) return;
        Location finalSpawn = spawn.clone();
        OreScheduler.runLaterForEntity(plugin, p, () -> {
            if (!p.isOnline()) return;
            if (OreScheduler.isFolia()) p.teleportAsync(finalSpawn);
            else p.teleport(finalSpawn);

            long remaining = s.remainingMs();
            if (remaining > 0) p.sendMessage("§cYou are still jailed for " + TimeText.format(remaining));
            else if (s.endEpochMs == 0) p.sendMessage("§cYou are permanently jailed.");
        }, 5L);
    }

    public boolean isCommandBlockedFor(Player p, String baseCmd) {
        return active.containsKey(p.getUniqueId()) && blockedCommands.contains(baseCmd.toLowerCase(Locale.ROOT));
    }

    public Set<String> getBlockedCommands() { return blockedCommands; }
}
