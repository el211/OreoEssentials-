// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/RtpCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rtp.RtpConfig;
import fr.elias.oreoEssentials.rtp.RtpCrossServerBridge;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RtpCommand implements OreoCommand {

    @Override
    public String name() {
        return "rtp";
    }

    @Override
    public List<String> aliases() {
        return List.of("randomtp");
    }

    @Override
    public String permission() {
        return "oreo.rtp";
    }

    @Override
    public String usage() {
        return "[world]";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return true;
        }

        OreoEssentials plugin = OreoEssentials.get();
        RtpConfig cfg = plugin.getRtpConfig();

        if (cfg == null || !cfg.isEnabled()) {
            Lang.send(p, "rtp.disabled", null, p);
            return true;
        }

        // --- Cooldown RTP (per-permission) ---
        int cooldown = cfg.cooldownFor(p);
        if (cooldown > 0) {

            Long last = plugin.getRtpCooldownCache().get(p.getUniqueId());
            long now = System.currentTimeMillis();

            if (last != null) {
                long elapsed = (now - last) / 1000;
                long remain = cooldown - elapsed;

                if (remain > 0) {
                    Lang.send(p,
                            "rtp.cooldown-wait",
                            java.util.Map.of(
                                    "seconds", String.valueOf(remain),
                                    "label", label
                            ),
                            p
                    );
                    return true;
                }
            }

            // Enregistrer nouveau timestamp
            plugin.getRtpCooldownCache().put(p.getUniqueId(), now);
        }

        // 1) Decide target world (optionally from argument)
        String requestedWorld = (args.length >= 1) ? args[0] : null;
        String targetWorldName = cfg.chooseTargetWorld(p, requestedWorld);

        if (targetWorldName == null || targetWorldName.isBlank()) {
            Lang.send(p, "rtp.no-world", null, p);
            return true;
        }

        // 2) Cross-server vs local decision
        String localServer  = plugin.getConfigService().serverName();
        String targetServer = cfg.serverForWorld(targetWorldName); // may be null

        boolean crossEnabled = cfg.isCrossServerEnabled();
        boolean sameServer   = (targetServer == null) || targetServer.equalsIgnoreCase(localServer);

        if (crossEnabled && !sameServer) {
            // Cross-server RTP path
            Lang.send(p,
                    "rtp.cross-switch",
                    java.util.Map.of(
                            "server", targetServer,
                            "world", targetWorldName
                    ),
                    p
            );

            RtpCrossServerBridge bridge = plugin.getRtpBridge();
            if (bridge != null) {
                // New flow: send RtpTeleportRequestPacket + proxy switch.
                bridge.requestCrossServerRtp(p, targetWorldName, targetServer);
            } else {
                // Fallback: at least move the player to the server (requires /server support)
                p.performCommand("server " + targetServer);
            }
            return true;
        }

        // 3) Local RTP on this node
        return doLocalRtp(plugin, p, targetWorldName);
    }

    /**
     * Local-only RTP logic (no cross-server). Can be reused by the cross-server bridge
     * and join listeners to actually perform the random teleport on the destination node.
     */
    public static boolean doLocalRtp(OreoEssentials plugin,
                                     Player p,
                                     String targetWorldName) {
        if (p == null || !p.isOnline()) return false;

        RtpConfig cfg = plugin.getRtpConfig();
        if (cfg == null) {
            Lang.send(p, "rtp.not-configured", null, p);
            return false;
        }

        World world = Bukkit.getWorld(targetWorldName);
        if (world == null) {
            Lang.send(p,
                    "rtp.world-not-loaded",
                    java.util.Map.of("world", targetWorldName),
                    p
            );
            return false;
        }

        // Check allowlist for that world too
        if (!cfg.allowedWorlds().isEmpty() && !cfg.allowedWorlds().contains(world.getName())) {
            Lang.send(p, "rtp.not-allowed-world", null, p);
            return false;
        }

        // Compute radius using per-world + tier permissions
        int radius = cfg.radiusFor(p, null);

        // Always use world spawn as RTP center
        Location center = world.getSpawnLocation();


        // "Trying random teleport in ..."
        Lang.send(p,
                "rtp.trying",
                java.util.Map.of(
                        "world", world.getName(),
                        "radius", String.valueOf(radius)
                ),
                p
        );

        Location dest = findSafeLocation(world, center, radius, cfg);
        if (dest == null) {
            Lang.send(p, "rtp.no-safe-spot", null, p);
            return false;
        }

        boolean ok = p.teleport(dest);
        if (ok) {
            // "Teleported to x, y, z in world"
            Lang.send(p,
                    "rtp.teleported",
                    java.util.Map.of(
                            "x", String.valueOf(dest.getBlockX()),
                            "y", String.valueOf(dest.getBlockY()),
                            "z", String.valueOf(dest.getBlockZ()),
                            "world", world.getName()
                    ),
                    p
            );
        } else {
            Lang.send(p, "rtp.failed", null, p);
        }
        return ok;
    }

    private static Location findSafeLocation(World world, Location center, int radius, RtpConfig cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int attempts = cfg.attempts();
        int minY = Math.max(5, cfg.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, cfg.maxY());
        Set<String> unsafe = cfg.unsafeBlocks();

        for (int i = 0; i < attempts; i++) {
            double angle = rnd.nextDouble(0, Math.PI * 2);
            double dist = rnd.nextDouble(0, Math.max(1, radius));

            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            Chunk chunk = world.getChunkAt(new Location(world, x, 0, z));
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }

            // Scan downward to find ground from the top
            int y = Math.min(maxY, world.getMaxHeight() - 1);
            while (y > minY && world.getBlockAt(x, y, z).isEmpty()) {
                y--;
            }

            if (y <= minY) {
                continue;
            }

            Block feet   = world.getBlockAt(x, y + 1, z);
            Block head   = world.getBlockAt(x, y + 2, z);
            Block ground = world.getBlockAt(x, y, z);

            if (!feet.isEmpty() || !head.isEmpty()) {
                continue;
            }

            String groundType = ground.getType().name();
            if (unsafe.contains(groundType)) {
                continue;
            }

            if (ground.isLiquid()) {
                continue;
            }

            // Found a valid location
            Location tp = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            tp.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
            tp.setPitch(0f);
            return tp;
        }
        return null;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // Only tab-complete first argument: [world]
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        OreoEssentials plugin = OreoEssentials.get();
        RtpConfig cfg = plugin.getRtpConfig();

        Set<String> suggestions = new HashSet<>();

        // Allowed worlds: if empty, propose all loaded worlds
        Set<String> allowed = cfg.allowedWorlds();
        if (allowed.isEmpty()) {
            for (World w : Bukkit.getWorlds()) {
                suggestions.add(w.getName());
            }
        } else {
            suggestions.addAll(allowed);
        }

        // Add cross-server mapped worlds (keys are world names)
        suggestions.addAll(cfg.worldServerMappings().keySet());

        return suggestions.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted()
                .toList();
    }
}
