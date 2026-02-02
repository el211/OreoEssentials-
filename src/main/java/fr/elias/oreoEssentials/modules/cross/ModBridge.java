package fr.elias.oreoEssentials.modules.cross;

import com.google.gson.Gson;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.cross.rabbit.packets.CrossModPacket;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.modules.cross.rabbit.packets.CrossInvPacket;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.UUID;


public final class ModBridge {

    private static final Gson GSON = new Gson();

    private final OreoEssentials plugin;
    private final PacketManager packets;
    private final String thisServer;

    public ModBridge(OreoEssentials plugin, PacketManager packets, String thisServer) {
        this.plugin = plugin;
        this.packets = packets;
        this.thisServer = thisServer;

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[MOD-BRIDGE] PacketManager not available; running in local-only mode.");
            return;
        }

        plugin.getLogger().info("[MOD-BRIDGE] Subscribing mod messages on server=" + thisServer);

        packets.subscribe(CrossInvPacket.class, (channel, raw) -> {
            try {
                String json = raw.getJson();
                CrossModPacket pkt = GSON.fromJson(json, CrossModPacket.class);
                if (pkt == null || pkt.getKind() == null || !"MOD".equals(pkt.getKind())) {
                    return; // not a moderation message
                }
                handleIncoming(pkt);
            } catch (Throwable t) {
                plugin.getLogger().warning("[MOD-BRIDGE] Incoming mod packet error: " + t.getMessage());
            }
        });

        plugin.getLogger().info("[MOD-BRIDGE] Ready on server=" + thisServer);
    }


    public void kill(UUID targetId, String targetName) {
        if (tryLocalKill(targetId)) return;
        sendRemote(CrossModPacket.Action.KILL, targetId, targetName, null);
    }

    public void kick(UUID targetId, String targetName, String reason) {
        if (tryLocalKick(targetId, reason)) return;
        sendRemote(CrossModPacket.Action.KICK, targetId, targetName, reason);
    }

    public void ban(UUID targetId, String targetName, String reason) {
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "ban " + targetName + " " + (reason == null ? "ModGUI" : reason)
        );
        sendRemote(CrossModPacket.Action.KICK, targetId, targetName, reason);
    }

    public void freezeToggle(UUID targetId, String targetName, long seconds) {
        if (tryLocalFreezeToggle(targetId, seconds)) return;
        sendRemote(CrossModPacket.Action.FREEZE_TOGGLE, targetId, targetName, Long.toString(seconds));
    }

    public void vanishToggle(UUID targetId, String targetName) {
        if (tryLocalVanishToggle(targetId)) return;
        sendRemote(CrossModPacket.Action.VANISH_TOGGLE, targetId, targetName, null);
    }

    public void gamemodeCycle(UUID targetId, String targetName) {
        if (tryLocalGamemodeCycle(targetId)) return;
        sendRemote(CrossModPacket.Action.GAMEMODE_CYCLE, targetId, targetName, null);
    }
    public void heal(UUID targetId, String targetName) {
        if (tryLocalHeal(targetId)) return;
        sendRemote(CrossModPacket.Action.HEAL, targetId, targetName, null);
    }

    public void feed(UUID targetId, String targetName) {
        if (tryLocalFeed(targetId)) return;
        sendRemote(CrossModPacket.Action.FEED, targetId, targetName, null);
    }



    private boolean tryLocalKill(UUID targetId) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;
        p.setHealth(0.0);
        return true;
    }

    private boolean tryLocalKick(UUID targetId, String reason) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;
        p.kickPlayer(reason == null ? "Kicked" : reason);
        return true;
    }

    private boolean tryLocalFreezeToggle(UUID targetId, long seconds) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;

        var fm = plugin.getFreezeManager();
        if (fm == null) return false;

        if (fm.isFrozen(targetId)) {
            fm.unfreeze(p);
        } else {
            fm.freeze(p, null, seconds);
        }
        return true;
    }
    private boolean tryLocalHeal(UUID targetId) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;

        var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = (attr != null ? attr.getValue() : 20.0);
        p.setHealth(max);
        return true;
    }

    private boolean tryLocalFeed(UUID targetId) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;

        p.setFoodLevel(20);
        p.setSaturation(20);
        return true;
    }

    private boolean tryLocalVanishToggle(UUID targetId) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vanish " + p.getName());
        return true;
    }

    private boolean tryLocalGamemodeCycle(UUID targetId) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;

        GameMode next = switch (p.getGameMode()) {
            case SURVIVAL -> GameMode.CREATIVE;
            case CREATIVE -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
        p.setGameMode(next);
        p.sendMessage("§eYour gamemode is now §6" + next);
        return true;
    }



    private void sendRemote(CrossModPacket.Action action,
                            UUID targetId,
                            String targetName,
                            String reason) {
        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().fine("[MOD-BRIDGE] sendRemote but PacketManager unavailable.");
            return;
        }

        CrossModPacket pkt = new CrossModPacket();
        pkt.setAction(action);
        pkt.setTarget(targetId);
        pkt.setTargetName(targetName);
        pkt.setReason(reason);
        pkt.setSourceServer(thisServer);
        pkt.setTargetServer(null);

        String json = GSON.toJson(pkt);
        packets.sendPacket(PacketChannels.GLOBAL, new CrossInvPacket(json));
    }

    private void handleIncoming(CrossModPacket pkt) {

        if (pkt == null) {
            plugin.getLogger().warning("[MOD-BRIDGE] Received null CrossModPacket (ignored)");
            return;
        }

        if (pkt.getAction() == null) {
            plugin.getLogger().warning("[MOD-BRIDGE] Received packet with null action (ignored)");
            return;
        }

        if (pkt.getTarget() == null) {
            plugin.getLogger().warning("[MOD-BRIDGE] Received packet with null target for action "
                    + pkt.getAction());
            return;
        }

        if (pkt.getTargetServer() != null &&
                !thisServer.equalsIgnoreCase(pkt.getTargetServer())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(pkt.getTarget());
            if (p == null || !p.isOnline()) return;

            switch (pkt.getAction()) {

                case KILL -> p.setHealth(0.0);

                case KICK -> p.kickPlayer(
                        pkt.getReason() == null ? "Kicked" : pkt.getReason()
                );

                case BAN -> p.kickPlayer(
                        pkt.getReason() == null ? "Banned" : pkt.getReason()
                );

                case FREEZE_TOGGLE -> {
                    long seconds = 60L;
                    try {
                        if (pkt.getReason() != null)
                            seconds = Long.parseLong(pkt.getReason());
                    } catch (NumberFormatException ignored) { }

                    tryLocalFreezeToggle(pkt.getTarget(), seconds);
                }

                case VANISH_TOGGLE ->
                        tryLocalVanishToggle(pkt.getTarget());

                case GAMEMODE_CYCLE ->
                        tryLocalGamemodeCycle(pkt.getTarget());

                case HEAL ->
                        tryLocalHeal(pkt.getTarget());

                case FEED ->
                        tryLocalFeed(pkt.getTarget());
            }
        });
    }

}
