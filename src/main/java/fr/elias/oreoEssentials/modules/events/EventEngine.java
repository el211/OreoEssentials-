// File: src/main/java/fr/elias/oreoEssentials/events/EventEngine.java
package fr.elias.oreoEssentials.modules.events;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.DeathMessagePacket;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EventEngine implements Listener {

    private final EventConfig config;
    private final DeathMessageService deaths;

    private final Map<UUID, UUID> pvpLastOpponent = new ConcurrentHashMap<>();
    private final Set<String> announcedFirstJoin = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public EventEngine(EventConfig config, DeathMessageService deaths) {
        this.config = config;
        this.deaths = deaths;
    }

    private String colorize(String s) {
        return (s == null) ? null : s.replace('&', '§');
    }

    private void run(EventType type, Player p, Map<String, String> extra) {
        List<String> cmds = config.commands(type);
        if (cmds.isEmpty()) return;

        final String playerName = (p == null ? "" : p.getName());

        for (String raw : cmds) {
            String line = raw.replace("[playerName]", playerName);
            if (extra != null) {
                for (var e : extra.entrySet()) {
                    line = line.replace(e.getKey(), e.getValue());
                }
            }

            boolean asConsole = line.toLowerCase(Locale.ROOT).startsWith("asconsole!");
            boolean asPlayer  = line.toLowerCase(Locale.ROOT).startsWith("asplayer!");
            int delay         = extractDelay(line);

            String cmd = strip(line);
            cmd = colorize(cmd);

            final String cmdToRun      = cmd;
            final boolean asConsoleF   = asConsole;
            final boolean asPlayerF    = asPlayer;
            final Player playerContext = p;

            Runnable task = () -> {
                if (cmdToRun == null || cmdToRun.isEmpty()) return;

                if (asConsoleF) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdToRun);
                } else if (asPlayerF && playerContext != null) {
                    playerContext.performCommand(cmdToRun);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdToRun);
                }
            };

            if (delay > 0) {
                Bukkit.getScheduler().runTaskLater(
                        Bukkit.getPluginManager().getPlugin("OreoEssentials"),
                        task,
                        delay * 20L
                );
            } else {
                task.run();
            }
        }
    }

    private int extractDelay(String s) {
        String low = s.toLowerCase(Locale.ROOT);
        int idx = low.indexOf("delay!");
        if (idx < 0) return 0;
        String sub = low.substring(idx + 6).trim();
        String[] p = sub.split("\\s+");
        try {
            return Integer.parseInt(p[0]);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String strip(String s) {
        return s.replaceFirst("(?i)^asconsole!\\s*", "")
                .replaceFirst("(?i)^asplayer!\\s*", "")
                .replaceFirst("(?i)delay!\\s*\\d+\\s*", "")
                .trim();
    }

    // ---- Bukkit event handlers mapping to EventType ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            if (announcedFirstJoin.add(p.getUniqueId().toString())) {
                run(EventType.firstJoinServer, p, null);
            }
        }
        run(EventType.joinServer, p, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        run(EventType.quitServer, e.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent e) {
        run(EventType.playerRespawn, e.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        run(EventType.playerTeleport, e.getPlayer(), Map.of(
                "[fromWorld]", e.getFrom().getWorld().getName(),
                "[toWorld]", e.getTo().getWorld().getName()
        ));
        if (!e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            run(EventType.playerPreWorldChange, e.getPlayer(),
                    Map.of("[toWorld]", e.getTo().getWorld().getName()));
            // fire post-change one tick later to simulate "after"
            Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("OreoEssentials"),
                    () -> run(EventType.playerWorldChange, e.getPlayer(),
                            Map.of("[toWorld]", e.getTo().getWorld().getName()))
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent e) {
        run(EventType.bedEnter, e.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedLeave(PlayerBedLeaveEvent e) {
        run(EventType.bedLeave, e.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        run(EventType.playerWorldChange, e.getPlayer(),
                Map.of("[toWorld]", e.getPlayer().getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGM(PlayerGameModeChangeEvent e) {
        run(EventType.playerGameModeChange, e.getPlayer(),
                Map.of("[newGameMode]", e.getNewGameMode().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        run(EventType.playerKick, e.getPlayer(),
                Map.of("[reason]", String.valueOf(e.getReason())));
    }

    // "Ban" is not a direct player event; handle via AsyncPlayerPreLoginEvent with result KICK_BANNED
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {
            // Cannot run commands as player; run as console with [playerName]
            Player p = Bukkit.getPlayer(e.getUniqueId());
            run(EventType.playerBan, p, Map.of("[playerName]", e.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(PlayerLevelChangeEvent e) {
        run(EventType.playerLevelChange, e.getPlayer(),
                Map.of("[level]", String.valueOf(e.getNewLevel())));
    }

    // Void fall: simple Y<0 detector
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() != null && e.getTo().getY() < 0) {
            run(EventType.voidFall, e.getPlayer(), null);
        }
    }

    // Elytra glide start/stop
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        run(e.isGliding() ? EventType.elytraStartGlide : EventType.elytraEndGlide, p, null);
    }

    // Advancement
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdv(PlayerAdvancementDoneEvent e) {
        Advancement adv = e.getAdvancement();
        String key = adv.getKey().toString();
        run(EventType.advancementDone, e.getPlayer(), Map.of("[advancement]", key));
    }

    // Sneak + swap-hand (two signals close together -> sneakingSwapHandItems)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        run(EventType.swapHandItems, e.getPlayer(), null);
        if (e.getPlayer().isSneaking()) {
            run(EventType.sneakingSwapHandItems, e.getPlayer(), null);
        }
    }

    // PVP start/stop (very lightweight)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player damager = null;
        if (e.getDamager() instanceof Player p) {
            damager = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }

        if (damager != null && damager != victim) {
            UUID a = damager.getUniqueId(), b = victim.getUniqueId();
            if (!Objects.equals(pvpLastOpponent.get(a), b)) {
                pvpLastOpponent.put(a, b);
                pvpLastOpponent.put(b, a);
                run(EventType.pvpstart, damager, Map.of("[opponent]", victim.getName()));
                run(EventType.pvpstart, victim, Map.of("[opponent]", damager.getName()));
            }

            // ---- capture everything needed in final locals for the lambda ----
            final UUID aF = a, bF = b;
            final String damagerName = damager.getName();
            final String victimName  = victim.getName();

            // schedule stop after 15s of no hits between the pair
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("OreoEssentials"),
                    () -> {
                        if (Objects.equals(pvpLastOpponent.get(aF), bF)
                                && Objects.equals(pvpLastOpponent.get(bF), aF)) {
                            pvpLastOpponent.remove(aF);
                            pvpLastOpponent.remove(bF);
                            Player pa = Bukkit.getPlayer(aF), pb = Bukkit.getPlayer(bF);
                            if (pa != null) {
                                run(EventType.pvpstop, pa, Map.of("[opponent]", victimName));
                            }
                            if (pb != null) {
                                run(EventType.pvpstop, pb, Map.of("[opponent]", damagerName));
                            }
                        }
                    },
                    20L * 15
            );
        }
    }

    // Custom death message + playerKillPlayer event hooks
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        Player killer = dead.getKiller();
        EntityDamageEvent last = dead.getLastDamageCause();

        EntityType mob = null;
        String projectileType = null;
        ItemStack item = null;

        Entity rawKillerEntity = null;

        if (killer != null) {
            item = killer.getInventory().getItemInMainHand();
            rawKillerEntity = killer;
        } else if (last instanceof EntityDamageByEntityEvent ebe) {
            Entity src = ebe.getDamager();
            rawKillerEntity = src;
            if (src instanceof Projectile proj) {
                projectileType = proj.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                if (proj.getShooter() instanceof Player k2) {
                    killer = k2;
                    item = k2.getInventory().getItemInMainHand();
                    rawKillerEntity = k2;
                } else if (proj.getShooter() instanceof Entity shooter) {
                    mob = shooter.getType();
                    rawKillerEntity = shooter;
                }
            } else {
                mob = src.getType();
            }
        }

        // Fire event commands:
        if (killer != null) {
            run(EventType.playerKillPlayer, killer,
                    Map.of("[victim]", dead.getName()));
        }

        // --- MythicMobs support (optional) ---
        String mythicId = null;
        String mythicDisplay = null;
        if (MythicHook.isPresent() && rawKillerEntity != null && !(rawKillerEntity instanceof Player)) {
            var info = MythicHook.resolve(rawKillerEntity);
            if (info.isMythic()) {
                mythicId = info.internalName();
                mythicDisplay = info.displayName();
            }
        }

        // Build death message
        String msg = deaths.buildMessage(
                dead, killer, mob,
                last != null ? last.getCause() : null,
                item, projectileType,
                mythicId, mythicDisplay
        );

        // Set local death message
        e.setDeathMessage(msg);

        // NEW: Broadcast to all servers if cross-server messaging is enabled and message is not null
        OreoEssentials plugin = OreoEssentials.get();
        PacketManager pm = plugin.getPacketManager();

        if (pm != null && pm.isInitialized() && msg != null && !msg.isEmpty()) {
            String serverName = plugin.getConfigService().serverName();

            DeathMessagePacket packet = new DeathMessagePacket(
                    dead.getUniqueId(),
                    dead.getName(),
                    msg,
                    serverName
            );

            // *** FIXED: Use PacketChannels.GLOBAL instead of PacketChannel.broadcast() ***
            pm.sendPacket(fr.elias.oreoEssentials.rabbitmq.PacketChannels.GLOBAL, packet);

            plugin.getLogger().info("[Death/Broadcast] player=" + dead.getName()
                    + " server=" + serverName
                    + " msg=" + msg);
        }
    }
}
