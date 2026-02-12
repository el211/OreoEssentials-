package fr.elias.oreoEssentials.modules.tp.command;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.tp.service.TeleportService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Location;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class TpAcceptCommand implements OreoCommand {

    private final TeleportService tpa;

    public TpAcceptCommand(TeleportService tpa) {
        this.tpa = tpa;
    }

    @Override
    public String name() {
        return "tpaccept";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "oreo.tpa";
    }

    @Override
    public String usage() {
        return "[player]";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    private static String traceId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(2176782336L), 36)
                .toUpperCase(Locale.ROOT);
    }

    private boolean dbg() {
        try {
            var c = OreoEssentials.get().getConfig();
            return c.getBoolean("features.tpa.debug", c.getBoolean("debug", false));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean echo() {
        try {
            return OreoEssentials.get().getConfig()
                    .getBoolean("features.tpa.debug-echo-to-player", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void D(String id, String msg) {
        if (dbg()) OreoEssentials.get().getLogger().info("[TPACCEPT " + id + "] " + msg);
    }

    private void E(String id, String msg, Throwable t) {
        if (dbg()) {
            OreoEssentials.get().getLogger().log(
                    Level.WARNING,
                    "[TPACCEPT " + id + "] " + msg,
                    t
            );
        }
    }

    private void P(Player p, String id, String msg) {
        if (dbg() && echo()) {
            // Use Lang for debug messages too
            Lang.send(p, "tpa.debug.echo",
                    "<dark_gray>[<aqua>TPA</aqua>/<gray>%id%</gray>]</dark_gray> <gray>%message%</gray>",
                    Map.of("id", id, "message", msg));
        }
    }

    private static String ms(long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        return ms + "ms";
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player target)) return true;

        final String id = traceId();
        final long t0 = System.nanoTime();
        final String server = OreoEssentials.get().getConfigService().serverName();

        D(id, "enter player=" + target.getName() + " server=" + server);


        try {
            var broker = OreoEssentials.get().getTpaBroker();
            if (broker == null) {
                D(id, "broker=null (PacketManager disabled or not initialized)");
            } else {
                long t1 = System.nanoTime();
                boolean handled = broker.acceptCrossServer(target);
                D(id, "broker.acceptCrossServer -> " + handled + " in " + ms(t1));
                P(target, id, "cross-server accept " + (handled ? "✓" : "–"));

                if (handled) {
                    //  IMPORTANT: STOP HERE – NO COUNTDOWN ON THIS SERVER
                    D(id, "done (cross-server handled) in " + ms(t0));
                    return true;
                }
            }
        } catch (Throwable t) {
            E(id, "broker.acceptCrossServer threw", t);
        }

        try {
            Player requester = tpa.getRequester(target);
            if (requester == null || !requester.isOnline()) {
                D(id, "no requester found for target=" + target.getName());
                Lang.send(target, "tpa.accept.none",
                        "<red>No pending teleport requests.</red>");
                return true;
            }

            startTpaAcceptCountdown(target, requester, id);
            D(id, "local countdown started in " + ms(t0));
            return true;
        } catch (Throwable t) {
            E(id, "local countdown / teleportService.accept threw", t);
            Lang.send(target, "tpa.accept.failed",
                    "<red>Failed to accept teleport request.</red>");
            return true;
        }
    }


    private void startTpaAcceptCountdown(Player target, Player requester, String traceId) {
        OreoEssentials plugin = OreoEssentials.get();

        ConfigurationSection sec =
                plugin.getSettingsConfig().getRoot().getConfigurationSection("features.tpa");

        boolean enabled = sec != null && sec.getBoolean("cooldown", false);
        int seconds = (sec != null ? sec.getInt("cooldown-amount", 0) : 0);

        // No cooldown configured → teleport instantly (legacy behavior)
        if (!enabled || seconds <= 0) {
            D(traceId, "cooldown disabled or <=0 -> teleport immediately");
            runLocalAccept(target, traceId);
            return;
        }

        D(traceId, "starting TPA accept cooldown: " + seconds + "s");

        final Location startLoc = requester.getLocation().clone();

        new BukkitRunnable() {
            int remain = seconds;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    D(traceId, "target offline during countdown; cancel");
                    cancel();
                    return;
                }

                if (requester == null || !requester.isOnline()) {
                    D(traceId, "requester offline during countdown; cancel");
                    cancel();
                    return;
                }

                Location now = requester.getLocation();
                if (!now.getWorld().equals(startLoc.getWorld())
                        || now.getBlockX() != startLoc.getBlockX()
                        || now.getBlockY() != startLoc.getBlockY()
                        || now.getBlockZ() != startLoc.getBlockZ()) {

                    D(traceId, "requester moved during countdown; cancelling TPA");
                    cancel();
                    tpa.cancelRequestDueToMovement(target, requester);
                    return;
                }

                if (remain <= 0) {
                    cancel();
                    D(traceId, "countdown finished, running local accept");
                    runLocalAccept(target, traceId);
                    return;
                }

                String title = Lang.msgWithDefault(
                        "teleport.countdown.title",
                        "<yellow>Teleporting...</yellow>",
                        requester
                );

                String subtitle = Lang.msgWithDefault(
                        "teleport.countdown.subtitle",
                        "<gray>In <white>%seconds%</white>s...</gray>",
                        Map.of("seconds", String.valueOf(remain)),
                        requester
                );

                requester.sendTitle(title, subtitle, 0, 20, 0);
                remain--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }


    private void runLocalAccept(Player target, String id) {
        long t2 = System.nanoTime();
        boolean ok = tpa.accept(target);
        D(id, "teleportService.accept -> " + ok + " in " + ms(t2));
        P(target, id, "local accept " + (ok ? "✓" : "–"));

        if (!ok) {
            Lang.send(target, "tpa.accept.none",
                    "<red>No pending teleport requests.</red>");
            if (dbg()) {
                Lang.send(target, "tpa.accept.debug-hint",
                        "<gray>Debug: No request found in TeleportService.</gray>");
            }
        }
    }
}