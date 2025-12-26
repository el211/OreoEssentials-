// File: src/main/java/fr/elias/oreoEssentials/modgui/world/WorldTweaksListener.java
package fr.elias.oreoEssentials.modgui.world;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.cfg.ModGuiConfig;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityToggleGlideEvent;

import java.util.Map;

/**
 * World tweaks event listener.
 *
 * ✅ VERIFIED - Uses Lang.send() for 3 user messages
 *
 * Features:
 * - Nether: Allow water, beds, no fire damage, no ghast grief
 * - End: Void teleport, no dragon grief, no enderman grief
 * - World-specific: Disable elytra, tridents, PVP
 */
public class WorldTweaksListener implements Listener {

    private final ModGuiConfig cfg;

    public WorldTweaksListener(OreoEssentials plugin) {
        this.cfg = plugin.getModGuiService().cfg();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* =========================
       NETHER: WATER OVERRIDE
       ========================= */

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (w.getEnvironment() != Environment.NETHER) return;
        if (!cfg.netherAllowWater(w)) return;

        // Only care about water buckets
        ItemStack inHand = e.getItemStack();
        if (inHand == null || inHand.getType() != Material.WATER_BUCKET) return;

        // Place water manually
        e.setCancelled(true);
        Location target = e.getBlockClicked().getRelative(e.getBlockFace()).getLocation();
        target.getBlock().setType(Material.WATER);

        // Turn bucket into empty bucket
        inHand.setType(Material.BUCKET);
        p.getInventory().setItem(e.getHand(), inHand);
    }

    /* =========================
       NETHER: BEDS SAFE
       ========================= */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBedEnter(PlayerBedEnterEvent e) {
        World w = e.getBed().getWorld();
        if (w.getEnvironment() != Environment.NETHER) return;
        if (!cfg.netherAllowBeds(w)) return;

        // Just allow bed usage – explosion itself is handled elsewhere
        e.setCancelled(false);
    }

    // Cancel the actual explosion triggered by beds in Nether/End
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent e) {
        World w = e.getBlock().getWorld();
        if (w.getEnvironment() == Environment.NETHER && cfg.netherAllowBeds(w)) {
            e.setCancelled(true);
        }
        if (w.getEnvironment() == Environment.THE_END && cfg.netherAllowBeds(w)) {
            e.setCancelled(true);
        }
    }

    /* =========================
       NETHER: NO FIRE/LAVA DMG
       ========================= */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        World w = ent.getWorld();

        // Nether fire/lava immunity
        if (w.getEnvironment() == Environment.NETHER && cfg.netherNoFireDamage(w)) {
            switch (e.getCause()) {
                case FIRE:
                case FIRE_TICK:
                case LAVA:
                    e.setCancelled(true);
                    return;
                default:
                    break;
            }
        }

        // END: void teleport
        if (w.getEnvironment() == Environment.THE_END
                && e.getCause() == EntityDamageEvent.DamageCause.VOID
                && cfg.endVoidTeleport(w)
                && ent instanceof Player player) {

            e.setCancelled(true);
            Location spawn = w.getSpawnLocation();
            player.teleport(spawn);
            Lang.send(player, "modgui.world.void-saved",
                    "<yellow>You were saved from the void and teleported to spawn.</yellow>",
                    Map.of());
        }
    }

    /* =========================
       NETHER: NO GHAST GRIEF
       + END: NO DRAGON GRIEF
       ========================= */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent e) {
        Entity entity = e.getEntity();
        World w = e.getLocation().getWorld();
        if (w == null) return;

        // Nether: Ghast fireballs don't break blocks
        if (w.getEnvironment() == Environment.NETHER
                && cfg.netherNoGhastGrief(w)
                && entity != null
                && entity.getType() == EntityType.FIREBALL) {
            e.blockList().clear();
        }

        // End: Dragon doesn't break blocks
        if (w.getEnvironment() == Environment.THE_END
                && cfg.endNoDragonGrief(w)
                && entity instanceof EnderDragon) {
            e.blockList().clear();
        }
    }

    /* =========================
       END: NO ENDERMAN GRIEF
       ========================= */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEndermanChangeBlock(EntityChangeBlockEvent e) {
        Entity ent = e.getEntity();
        World w = ent.getWorld();
        if (w.getEnvironment() != Environment.THE_END) return;
        if (!cfg.endNoEndermanGrief(w)) return;

        if (ent instanceof Enderman) {
            e.setCancelled(true);
        }
    }

    /* =========================
       ELYTRA / TRIDENT / PVP
       ========================= */

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        World w = p.getWorld();
        if (cfg.disableElytra(w) && e.isGliding()) {
            e.setCancelled(true);
            Lang.send(p, "modgui.world.elytra-disabled",
                    "<red>Elytra flight is disabled in this world.</red>",
                    Map.of());
        }
    }

    @EventHandler
    public void onTridentLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Trident t)) return;
        if (!(t.getShooter() instanceof Player p)) return;
        if (cfg.disableTrident(p.getWorld())) {
            e.setCancelled(true);
            Lang.send(p, "modgui.world.trident-disabled",
                    "<red>Tridents are disabled in this world.</red>",
                    Map.of());
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e.getDamager();
        if (!(victim instanceof Player)) return;

        Player attacker = null;
        if (damager instanceof Player p) attacker = p;
        else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;

        if (attacker == null) return;

        World w = attacker.getWorld();
        // Global PVP toggle
        if (!cfg.pvpEnabled(w)) {
            e.setCancelled(true);
            return;
        }

        // Disable projectile-based PVP separately
        if (damager instanceof Projectile && cfg.disableProjectilePvp(w)) {
            e.setCancelled(true);
        }
    }
}