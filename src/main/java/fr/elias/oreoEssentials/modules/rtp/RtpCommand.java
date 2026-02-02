package fr.elias.oreoEssentials.modules.rtp;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            Lang.send(p, "rtp.disabled", "<red>RTP is currently disabled.</red>");
            return true;
        }

        int cooldown = cfg.cooldownFor(p);
        if (cooldown > 0) {
            Long last = plugin.getRtpCooldownCache().get(p.getUniqueId());
            long now = System.currentTimeMillis();

            if (last != null) {
                long elapsed = (now - last) / 1000;
                long remain = cooldown - elapsed;

                if (remain > 0) {
                    Lang.send(
                            p,
                            "rtp.cooldown-wait",
                            "<red>Please wait <yellow>%seconds%</yellow>s before using <yellow>/%label%</yellow> again.</red>",
                            Map.of(
                                    "seconds", String.valueOf(remain),
                                    "label", label
                            )
                    );
                    return true;
                }
            }
        }

        String requestedWorld = (args.length >= 1) ? args[0] : null;
        String targetWorldName = cfg.chooseTargetWorld(p, requestedWorld);

        if (targetWorldName == null || targetWorldName.isBlank()) {
            Lang.send(p, "rtp.no-world", "<red>No valid RTP world available.</red>");
            return true;
        }

        String localServer  = plugin.getConfigService().serverName();
        String targetServer = cfg.serverForWorld(targetWorldName); // may be null

        boolean crossEnabled = cfg.isCrossServerEnabled();
        boolean sameServer   = (targetServer == null) || targetServer.equalsIgnoreCase(localServer);

        var settings = plugin.getSettingsConfig();
        final boolean useWarmup = settings.rtpWarmupEnabled();
        final int seconds = settings.rtpWarmupSeconds();

        final boolean bypass = p.hasPermission("oreo.rtp.warmup.bypass") || !useWarmup || seconds <= 0;
        plugin.getLogger().info("[RTP] warmup=" + useWarmup + " seconds=" + seconds
                + " bypass=" + bypass + " hasBypassPerm=" + p.hasPermission("oreo.rtp.warmup.bypass"));

        if (crossEnabled && !sameServer) {
            final Runnable action = () -> {
                Lang.send(
                        p,
                        "rtp.cross-switch",
                        "<gray>Connecting you to <yellow>%server%</yellow> for random teleport in <aqua>%world%</aqua>...</gray>",
                        Map.of("server", targetServer, "world", targetWorldName)
                );

                RtpCrossServerBridge bridge = plugin.getRtpBridge();

                if (bridge != null) {
                    bridge.requestCrossServerRtp(p, targetWorldName, targetServer);
                }

                // switch server happens AFTER warmup
                p.performCommand("server " + targetServer);
            };

            if (bypass) action.run();
            else startRtpCountdown(plugin, p, seconds, targetWorldName, action);

            return true;
        }

        final Runnable action = () -> {
            boolean ok = doLocalRtp(plugin, p, targetWorldName);
            if (ok) {
                applyCooldownNow(plugin, p); // apply *after* success
            }
        };

        if (bypass) action.run();
        else startRtpCountdown(plugin, p, seconds, targetWorldName, action);

        return true;
    }

    public static void applyCooldownNow(OreoEssentials plugin, Player p) {
        if (plugin == null || p == null) return;
        RtpConfig cfg = plugin.getRtpConfig();
        if (cfg == null) return;
        int cd = cfg.cooldownFor(p);
        if (cd > 0) {
            plugin.getRtpCooldownCache().put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    private static void startRtpCountdown(OreoEssentials plugin,
                                          Player target,
                                          int seconds,
                                          String worldName,
                                          Runnable action) {

        final Location origin = target.getLocation().clone();

        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }

                if (hasMoved(target, origin)) {
                    cancel();
                    Lang.send(target, "rtp.cancelled-moved", "<red>Random teleport cancelled: you moved.</red>");
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    action.run();
                    return;
                }

                String title = Lang.msgWithDefault(
                        "rtp.warmup.title",
                        "<yellow>Teleporting in %seconds%s...</yellow>",
                        Map.of("seconds", String.valueOf(remaining)),
                        target
                );

                String subtitle = Lang.msgWithDefault(
                        "rtp.warmup.subtitle",
                        "<gray>World: <white>%world%</white> • in <yellow>%seconds%</yellow>s</gray>",
                        Map.of(
                                "seconds", String.valueOf(remaining),
                                "world", worldName
                        ),
                        target
                );

                target.sendTitle(title, subtitle, 0, 20, 0);

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private static boolean hasMoved(Player p, Location origin) {
        Location now = p.getLocation();

        if (now.getWorld() == null || origin.getWorld() == null) return true;
        if (!now.getWorld().equals(origin.getWorld())) return true;

        double dx = now.getX() - origin.getX();
        double dy = now.getY() - origin.getY();
        double dz = now.getZ() - origin.getZ();

        // Tiny nudge cancels warmup (consistent with home)
        return (dx * dx + dy * dy + dz * dz) > (0.05 * 0.05);
    }


    public static boolean doLocalRtp(OreoEssentials plugin,
                                     Player p,
                                     String targetWorldName) {

        if (p == null || !p.isOnline()) return false;

        RtpConfig cfg = plugin.getRtpConfig();
        if (cfg == null) {
            Lang.send(p, "rtp.not-configured", "<red>RTP is not properly configured.</red>");
            return false;
        }

        World world = Bukkit.getWorld(targetWorldName);
        if (world == null) {
            Lang.send(
                    p,
                    "rtp.world-not-loaded",
                    "<red>World <yellow>%world%</yellow> is not loaded.</red>",
                    Map.of("world", targetWorldName)
            );
            return false;
        }

        if (!cfg.allowedWorlds().isEmpty() && !cfg.allowedWorlds().contains(world.getName())) {
            Lang.send(p, "rtp.not-allowed-world", "<red>You cannot RTP to this world.</red>");
            return false;
        }

        int radius = cfg.radiusFor(p, List.of(world.getName()));
        int minRadius = cfg.minRadiusFor(p, world.getName());

        if (minRadius < 0) minRadius = 0;
        if (minRadius > radius) minRadius = radius;

        Location center = world.getSpawnLocation();

        Lang.send(
                p,
                "rtp.trying",
                "<gray>Finding safe location in <aqua>%world%</aqua> (radius: <yellow>%radius%</yellow>)...</gray>",
                Map.of(
                        "world", world.getName(),
                        "radius", String.valueOf(radius)
                )
        );

        Location dest = findSafeLocation(world, center, radius, minRadius, cfg);
        if (dest == null) {
            Lang.send(p, "rtp.no-safe-spot", "<red>Could not find a safe location. Try again.</red>");
            return false;
        }

        boolean ok = p.teleport(dest);
        if (ok) {
            Lang.send(
                    p,
                    "rtp.teleported",
                    "<green>Teleported to <white>%x%</white>, <white>%y%</white>, <white>%z%</white> in <aqua>%world%</aqua>!</green>",
                    Map.of(
                            "x", String.valueOf(dest.getBlockX()),
                            "y", String.valueOf(dest.getBlockY()),
                            "z", String.valueOf(dest.getBlockZ()),
                            "world", world.getName()
                    )
            );
        } else {
            Lang.send(p, "rtp.failed", "<red>Teleport failed. Please try again.</red>");
        }
        return ok;
    }


    private static Location findSafeLocation(World world,
                                             Location center,
                                             int radius,
                                             int minRadius,
                                             RtpConfig cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        int attempts = cfg.attempts();
        int minY = Math.max(5, cfg.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, cfg.maxY());
        Set<String> unsafe = cfg.unsafeBlocks();

        int min = Math.max(0, minRadius);
        int max = Math.max(min + 1, radius + 1);

        for (int i = 0; i < attempts; i++) {

            double angle = rnd.nextDouble(0, Math.PI * 2);
            double dist  = rnd.nextDouble(min, max);

            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            // Ensure chunk is loaded
            Chunk chunk = world.getChunkAt(new Location(world, x, 0, z));
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }

            int top = Math.min(maxY, world.getMaxHeight() - 1);

            if (world.getEnvironment() == World.Environment.NETHER) {
                top = Math.min(top, 120); // safely below the roof
            }

            int y = top;
            while (y > minY && world.getBlockAt(x, y, z).isEmpty()) {
                y--;
            }

            if (y <= minY) {
                continue;
            }

            Block feet   = world.getBlockAt(x, y + 1, z);
            Block head   = world.getBlockAt(x, y + 2, z);
            Block ground = world.getBlockAt(x, y, z);

            if (world.getEnvironment() == World.Environment.NETHER) {
                if (y >= 126) {
                    continue;
                }
                if (ground.getType() == Material.BEDROCK && y >= 120) {
                    continue;
                }
            }

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

            Location tp = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            tp.setYaw(rnd.nextFloat() * 360f);
            tp.setPitch(0f);
            return tp;
        }

        return null;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        OreoEssentials plugin = OreoEssentials.get();
        RtpConfig cfg = plugin.getRtpConfig();

        Set<String> suggestions = new HashSet<>();

        Set<String> allowed = cfg.allowedWorlds();
        if (allowed.isEmpty()) {
            for (World w : Bukkit.getWorlds()) {
                suggestions.add(w.getName());
            }
        } else {
            suggestions.addAll(allowed);
        }

        suggestions.addAll(cfg.worldServerMappings().keySet());

        return suggestions.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted()
                .toList();
    }
}