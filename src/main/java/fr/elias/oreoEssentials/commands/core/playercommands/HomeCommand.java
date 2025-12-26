// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/HomeCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class HomeCommand implements OreoCommand, TabCompleter {

    private final HomeService homes;

    public HomeCommand(HomeService homes) {
        this.homes = homes;
    }

    @Override public String name() { return "home"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.home"; }
    @Override public String usage() { return "<name>|list"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        final OreoEssentials plugin = OreoEssentials.get();

        // Feature toggle (settings.yml: features.home.enabled)
        if (!plugin.getSettingsConfig().isEnabled("home")) {
            Lang.send(player, "home.disabled",
                    "<red>Home feature is currently disabled.</red>");
            return true;
        }

        // /home or /home list -> show homes (CROSS-SERVER)
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            List<String> names = crossServerNames(player.getUniqueId());
            if (names.isEmpty()) {
                Lang.send(player, "home.no-homes",
                        "<yellow>You have no homes. Use <aqua>%sethome%</aqua> to create one.</yellow>",
                        Map.of("sethome", "/sethome")
                );
                return true;
            }

            // Format list with colors (names will be inserted as-is)
            String list = names.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));

            Lang.send(player, "home.list",
                    "<gray>Your homes:</gray> <aqua>%homes%</aqua>",
                    Map.of("homes", list)
            );
            Lang.send(player, "home.tip",
                    "<gray>Use <yellow>/%label% <name></yellow> to teleport.</gray>",
                    Map.of("label", label)
            );
            return true;
        }

        final String raw = args[0];
        final String key = normalize(raw);

        // ---- Cooldown / countdown (features.home.cooldown) ----
        FileConfiguration settings = plugin.getSettingsConfig().getRoot();
        ConfigurationSection homeSection = settings.getConfigurationSection("features.home");
        final boolean useCooldown = homeSection != null && homeSection.getBoolean("cooldown", false);
        final int seconds = homeSection != null ? homeSection.getInt("cooldown-amount", 0) : 0;

        // OP bypass + invalid bypass
        final boolean bypass = player.isOp() || !useCooldown || seconds <= 0;

        // Where does the home live?
        final String localServer = homes.localServer();
        final String homeServer = homes.homeServer(player.getUniqueId(), key);
        final String targetServer = (homeServer == null ? localServer : homeServer);

        // check existence BEFORE starting countdown
        if (targetServer.equalsIgnoreCase(localServer)) {
            // Local server: must exist locally
            if (homes.getHome(player.getUniqueId(), key) == null) {
                Lang.send(player, "home.not-found",
                        "<red>Home <yellow>%name%</yellow> not found.</red>",
                        Map.of("name", raw));
                suggestClosest(player, key);
                return true;
            }
        } else {
            // Cross-server: must exist in cross-server index (homeServer must be set)
            if (homeServer == null || homeServer.isBlank()) {
                Lang.send(player, "home.not-found",
                        "<red>Home <yellow>%name%</yellow> not found.</red>",
                        Map.of("name", raw));
                suggestClosest(player, key);
                return true;
            }
        }

        // Local teleport (with optional countdown)
        if (targetServer.equalsIgnoreCase(localServer)) {

            final Runnable action = () -> {
                Location loc = homes.getHome(player.getUniqueId(), key);
                if (loc == null) {
                    Lang.send(player, "home.not-found",
                            "<red>Home <yellow>%name%</yellow> not found.</red>",
                            Map.of("name", raw)
                    );
                    suggestClosest(player, key);
                    return;
                }
                player.teleport(loc);
                Lang.send(player, "home.teleported",
                        "<green>Teleported to home <yellow>%name%</yellow>.</green>",
                        Map.of("name", key)
                );
            };

            if (bypass) {
                action.run();
            } else {
                startCountdown(player, seconds, key, action);
            }
            return true;
        }

        // Cross-server (with optional countdown before switching)
        final Runnable action = () -> {

            // Respect cross-server toggle for homes
            var cs = plugin.getCrossServerSettings();
            if (!cs.homes()) {
                Lang.send(player, "home.cross-disabled",
                        "<red>Cross-server homes are disabled.</red>"
                );
                Lang.send(player, "home.cross-disabled-tip",
                        "<gray>Ask an admin to enable cross-server homes or use <yellow>/server %server%</yellow> then <yellow>/home %name%</yellow>.</gray>",
                        Map.of("server", targetServer, "name", key)
                );
                return;
            }

            // Cross-server: publish to the TARGET SERVER'S QUEUE (not global), then proxy switch
            final PacketManager pm = plugin.getPacketManager();

            if (pm != null && pm.isInitialized()) {
                final String requestId = UUID.randomUUID().toString();
                plugin.getLogger().info("[HOME/SEND] from=" + homes.localServer()
                        + " player=" + player.getUniqueId()
                        + " nameArg='" + key + "' -> targetServer=" + targetServer
                        + " requestId=" + requestId);

                HomeTeleportRequestPacket pkt = new HomeTeleportRequestPacket(player.getUniqueId(), key, targetServer, requestId);
                PacketChannel targetChannel = PacketChannel.individual(targetServer);
                pm.sendPacket(targetChannel, pkt);
            } else {
                Lang.send(player, "home.messaging-disabled",
                        "<red>Cross-server messaging is disabled.</red>"
                );
                Lang.send(player, "home.messaging-disabled-tip",
                        "<gray>Ask an admin to enable messaging or use <yellow>/server %server%</yellow> then <yellow>/home %name%</yellow>.</gray>",
                        Map.of("server", targetServer, "name", key)
                );
                return;
            }

            // Switch via proxy
            boolean switched = sendPlayerToServer(player, targetServer);
            if (switched) {
                Lang.send(player, "home.sending",
                        "<gray>Sending you to <yellow>%server%</yellow> for home <aqua>%name%</aqua>...</gray>",
                        Map.of("server", targetServer, "name", key)
                );
            } else {
                Lang.send(player, "home.switch-failed",
                        "<red>Failed to switch to server <yellow>%server%</yellow>.</red>",
                        Map.of("server", targetServer)
                );
                Lang.send(player, "home.switch-failed-tip",
                        "<gray>Try <yellow>/server %server%</yellow> manually.</gray>",
                        Map.of("server", targetServer)
                );
            }
        };

        if (bypass) {
            action.run();
        } else {
            startCountdown(player, seconds, key, action);
        }
        return true;
    }

    /* ---------------- tab completion ---------------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return crossServerNames(p.getUniqueId()).stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private boolean homeExistsSomewhere(UUID playerId, String homeKey) {
        try {
            // If HomeService can resolve a server for the home, it exists somewhere (cross-server index)
            String server = homes.homeServer(playerId, homeKey);
            if (server != null && !server.isBlank()) return true;

            // Otherwise, it might be local-only (or index not available): try local lookup
            return homes.getHome(playerId, homeKey) != null;
        } catch (Throwable ignored) {
            // On any failure, fall back to local lookup
            return homes.getHome(playerId, homeKey) != null;
        }
    }

    /* ---------------- helpers ---------------- */

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** Cross-server names (plain names). Falls back to empty if unavailable. */
    private List<String> crossServerNames(UUID id) {
        try {
            // Preferred: use aggregated list from HomeService
            Set<String> set = homes.allHomeNames(id);
            if (set != null) {
                return set.stream()
                        .filter(Objects::nonNull)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        } catch (Throwable ignored) {}
        // Very last resort: empty (do not read local-only to avoid inconsistency with /homes)
        return Collections.emptyList();
    }

    private void suggestClosest(Player p, String key) {
        List<String> suggestions = crossServerNames(p.getUniqueId()).stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))
                .limit(5)
                .collect(Collectors.toList());

        if (!suggestions.isEmpty()) {
            String joined = String.join(", ", suggestions);
            Lang.send(p, "home.suggest",
                    "<gray>Did you mean:</gray> <aqua>%suggestions%</aqua>",
                    Map.of("suggestions", joined)
            );
        }
    }

    /** Proxy switch */
    private boolean sendPlayerToServer(Player p, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", b.toByteArray());
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Shows a big title countdown on the player, cancels if he moves,
     * then runs the action at the end.
     */
    private void startCountdown(Player target, int seconds, String homeName, Runnable action) {
        final OreoEssentials plugin = OreoEssentials.get();
        final Location origin = target.getLocation().clone();

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }

                if (hasBodyMoved(target, origin)) {
                    cancel();
                    Lang.send(target, "home.cancelled-moved",
                            "<red>Teleport cancelled: you moved.</red>",
                            Map.of("name", homeName)
                    );
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    action.run();
                    return;
                }

                // Use Lang.msgWithDefault for title/subtitle with variables
                String title = Lang.msgWithDefault(
                        "teleport.countdown.title",
                        "<yellow>Teleporting...</yellow>",
                        target
                );

                String subtitle = Lang.msgWithDefault(
                        "teleport.countdown.subtitle",
                        "<gray>In <white>%seconds%</white>s...</gray>",
                        Map.of("seconds", String.valueOf(remaining)),
                        target
                );

                target.sendTitle(title, subtitle, 0, 20, 0);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean hasBodyMoved(Player p, Location origin) {
        Location now = p.getLocation();

        if (now.getWorld() == null || origin.getWorld() == null) return true;
        if (!now.getWorld().equals(origin.getWorld())) return true;

        // cancel if moved even slightly (ignore head rotation)
        double dx = now.getX() - origin.getX();
        double dy = now.getY() - origin.getY();
        double dz = now.getZ() - origin.getZ();

        // tolerance: 0.05 blocks
        return (dx * dx + dy * dy + dz * dz) > (0.05 * 0.05);
    }
}