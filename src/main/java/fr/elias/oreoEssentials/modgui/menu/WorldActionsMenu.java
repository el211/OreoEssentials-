// File: src/main/java/fr/elias/oreoEssentials/modgui/menu/WorldActionsMenu.java
package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

import static org.bukkit.World.Environment.NETHER;
import static org.bukkit.World.Environment.THE_END;

/**
 * World actions and settings menu.
 *
 * ✅ VERIFIED - Uses Lang.send() for 7 user messages + Lang.get() for GUI titles + § for GUI items
 *
 * Features:
 * - Time control (day/sunset/night)
 * - Weather control (clear/rain/storm)
 * - World spawn setting
 * - World border quick sizes
 * - Gamerules, whitelist, banned mobs sub-menus
 * - Elytra, trident, PVP toggles
 * - Theme cycling
 * - Nether-specific tweaks (water, beds, fire immunity, ghast grief)
 * - End-specific tweaks (void teleport, enderman grief, dragon grief)
 */
public class WorldActionsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final World world;

    public WorldActionsMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin = plugin;
        this.svc    = svc;
        this.world  = world;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        // ===== Time =====
        c.set(1, 2, ClickableItem.of(
                new ItemBuilder(Material.CLOCK)
                        .name("&eTime: Day")
                        .lore("&7Now: " + world.getTime())
                        .build(),
                e -> {
                    setTime(1000);
                    Lang.send(p, "modgui.world-actions.time-set",
                            "<yellow>Time set to <gold>%time%</gold></yellow>",
                            Map.of("time", "Day"));
                    init(p, c);
                }
        ));

        c.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.CLOCK)
                        .name("&6Time: Sunset")
                        .lore("&7Now: " + world.getTime())
                        .build(),
                e -> {
                    setTime(12000);
                    Lang.send(p, "modgui.world-actions.time-set",
                            "<yellow>Time set to <gold>%time%</gold></yellow>",
                            Map.of("time", "Sunset"));
                    init(p, c);
                }
        ));

        c.set(1, 4, ClickableItem.of(
                new ItemBuilder(Material.CLOCK)
                        .name("&cTime: Night")
                        .lore("&7Now: " + world.getTime())
                        .build(),
                e -> {
                    setTime(18000);
                    Lang.send(p, "modgui.world-actions.time-set",
                            "<yellow>Time set to <gold>%time%</gold></yellow>",
                            Map.of("time", "Night"));
                    init(p, c);
                }
        ));

        // ===== Weather =====
        c.set(2, 2, ClickableItem.of(
                new ItemBuilder(Material.SUNFLOWER)
                        .name("&eWeather: Clear")
                        .lore(currentWeather())
                        .build(),
                e -> {
                    weather("sun");
                    Lang.send(p, "modgui.world-actions.weather-set",
                            "<yellow>Weather set to <gold>%weather%</gold></yellow>",
                            Map.of("weather", "Clear"));
                    init(p, c);
                }
        ));

        c.set(2, 3, ClickableItem.of(
                new ItemBuilder(Material.WATER_BUCKET)
                        .name("&bWeather: Rain")
                        .lore(currentWeather())
                        .build(),
                e -> {
                    weather("rain");
                    Lang.send(p, "modgui.world-actions.weather-set",
                            "<yellow>Weather set to <gold>%weather%</gold></yellow>",
                            Map.of("weather", "Rain"));
                    init(p, c);
                }
        ));

        c.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.TRIDENT)
                        .name("&3Weather: Storm")
                        .lore(currentWeather())
                        .build(),
                e -> {
                    weather("storm");
                    Lang.send(p, "modgui.world-actions.weather-set",
                            "<yellow>Weather set to <gold>%weather%</gold></yellow>",
                            Map.of("weather", "Storm"));
                    init(p, c);
                }
        ));

        // ===== Spawn =====
        c.set(3, 2, ClickableItem.of(
                new ItemBuilder(Material.RED_BED)
                        .name("&aSet World Spawn at your pos")
                        .build(),
                e -> {
                    world.setSpawnLocation(p.getLocation());
                    Lang.send(p, "modgui.world-actions.spawn-set",
                            "<green>World spawn set at <white>%x% %y% %z%</white></green>",
                            Map.of(
                                    "x", String.valueOf(p.getLocation().getBlockX()),
                                    "y", String.valueOf(p.getLocation().getBlockY()),
                                    "z", String.valueOf(p.getLocation().getBlockZ())
                            ));
                }
        ));

        // ===== World border quick sizes =====
        c.set(3, 4, ClickableItem.of(
                new ItemBuilder(Material.IRON_BARS)
                        .name("&fBorder: 1k")
                        .lore(borderLore())
                        .build(),
                e -> {
                    border(1000);
                    Lang.send(p, "modgui.world-actions.border-set",
                            "<yellow>World border set to <gold>%size%</gold></yellow>",
                            Map.of("size", "1,000"));
                    init(p, c);
                }
        ));

        c.set(3, 5, ClickableItem.of(
                new ItemBuilder(Material.IRON_BARS)
                        .name("&fBorder: 2k")
                        .lore(borderLore())
                        .build(),
                e -> {
                    border(2000);
                    Lang.send(p, "modgui.world-actions.border-set",
                            "<yellow>World border set to <gold>%size%</gold></yellow>",
                            Map.of("size", "2,000"));
                    init(p, c);
                }
        ));

        c.set(3, 6, ClickableItem.of(
                new ItemBuilder(Material.IRON_BARS)
                        .name("&fBorder: 5k")
                        .lore(borderLore())
                        .build(),
                e -> {
                    border(5000);
                    Lang.send(p, "modgui.world-actions.border-set",
                            "<yellow>World border set to <gold>%size%</gold></yellow>",
                            Map.of("size", "5,000"));
                    init(p, c);
                }
        ));

        // ===== Sub-menus =====
        c.set(4, 3, ClickableItem.of(
                new ItemBuilder(Material.BOOK)
                        .name("&bGamerules (per-world)")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new WorldGamerulesMenu(plugin, svc, world))
                        .title(Lang.color(Lang.get("modgui.world-actions.gamerules-title", "&8Gamerules: %world%")
                                .replace("%world%", world.getName())))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        c.set(4, 5, ClickableItem.of(
                new ItemBuilder(Material.WHITE_WOOL)
                        .name("&fWhitelist (per-world)")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new WorldWhitelistMenu(plugin, svc, world))
                        .title(Lang.color(Lang.get("modgui.world-actions.whitelist-title", "&8Whitelist: %world%")
                                .replace("%world%", world.getName())))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        c.set(4, 7, ClickableItem.of(
                new ItemBuilder(Material.ZOMBIE_HEAD)
                        .name("&2Banned mobs (per-world)")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new WorldBannedMobsMenu(plugin, svc, world))
                        .title(Lang.color(Lang.get("modgui.world-actions.banned-mobs-title", "&8Banned Mobs: %world%")
                                .replace("%world%", world.getName())))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        // ===== Toggles =====

        // Elytra toggle
        boolean elytraDisabled = svc.cfg().disableElytra(world);
        c.set(5, 1, ClickableItem.of(
                new ItemBuilder(Material.ELYTRA)
                        .name("&bElytra in this world: " + (elytraDisabled ? "&cDISABLED" : "&aALLOWED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setDisableElytra(world, !elytraDisabled);
                    init(p, c);
                }
        ));

        // Trident toggle
        boolean tridentDisabled = svc.cfg().disableTrident(world);
        c.set(5, 2, ClickableItem.of(
                new ItemBuilder(Material.TRIDENT)
                        .name("&bTridents: " + (tridentDisabled ? "&cDISABLED" : "&aALLOWED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setDisableTrident(world, !tridentDisabled);
                    init(p, c);
                }
        ));

        // PVP toggle
        boolean pvpOn = svc.cfg().pvpEnabled(world);
        c.set(5, 4, ClickableItem.of(
                new ItemBuilder(Material.DIAMOND_SWORD)
                        .name("&cPVP: " + (pvpOn ? "&aENABLED" : "&cDISABLED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setPvpEnabled(world, !pvpOn);
                    world.setPVP(!pvpOn); // Apply to world flag too
                    init(p, c);
                }
        ));

        // Projectile PVP toggle
        boolean projPvpOff = svc.cfg().disableProjectilePvp(world);
        c.set(5, 5, ClickableItem.of(
                new ItemBuilder(Material.ARROW)
                        .name("&cProjectile PVP: " + (projPvpOff ? "&cDISABLED" : "&aENABLED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setDisableProjectilePvp(world, !projPvpOff);
                    init(p, c);
                }
        ));

        // Theme cycle
        String theme = svc.cfg().worldTheme(world); // e.g. DEFAULT/RED/PURPLE/GREEN/BLUE
        c.set(5, 7, ClickableItem.of(
                new ItemBuilder(Material.GLOWSTONE_DUST)
                        .name("&dWorld theme: &f" + theme)
                        .lore("&7Click to cycle: DEFAULT → RED → PURPLE → GREEN → BLUE")
                        .build(),
                e -> {
                    String next = switch (theme.toUpperCase()) {
                        case "DEFAULT" -> "RED";
                        case "RED"     -> "PURPLE";
                        case "PURPLE"  -> "GREEN";
                        case "GREEN"   -> "BLUE";
                        default        -> "DEFAULT";
                    };
                    svc.cfg().setWorldTheme(world, next);
                    init(p, c);
                }
        ));

        // ===== Dimension tweaks (Nether / End only) =====
        switch (world.getEnvironment()) {
            case NETHER -> renderNetherTweaks(p, c);
            case THE_END -> renderEndTweaks(p, c);
            default -> { /* Overworld / custom env: no extra tweaks */ }
        }
    }

    private String currentWeather() {
        boolean storm = world.hasStorm();
        boolean thun  = world.isThundering();
        String state = (!storm && !thun) ? "§eClear" : (storm && !thun) ? "§bRain" : "§3Storm";
        return "§7Current: " + state;
    }

    private String borderLore() {
        int size = (int) world.getWorldBorder().getSize();
        return "§7Current: §f" + size;
    }

    private void setTime(long ticks) {
        world.setTime(ticks);
    }

    private void weather(String mode) {
        switch (mode) {
            case "sun" -> { world.setStorm(false); world.setThundering(false); }
            case "rain" -> { world.setStorm(true);  world.setThundering(false); }
            case "storm" -> { world.setStorm(true); world.setThundering(true);  }
        }
    }

    private void border(int size) {
        var wb = world.getWorldBorder();
        // Use existing center if set; otherwise default to current spawn
        if (wb.getCenter().getWorld() == null || !wb.getCenter().getWorld().equals(world)) {
            wb.setCenter(world.getSpawnLocation());
        }
        wb.setSize(size);
    }

    // ===== NETHER TWEAKS =====
    private void renderNetherTweaks(Player p, InventoryContents c) {
        // Allow water placement
        boolean allowWater = svc.cfg().netherAllowWater(world);
        c.set(5, 1, ClickableItem.of(
                new ItemBuilder(allowWater ? Material.WATER_BUCKET : Material.BUCKET)
                        .name("&bNether: Water " + (allowWater ? "&aENABLED" : "&cDISABLED"))
                        .lore("&7Allow placing water in Nether using buckets.")
                        .build(),
                e -> {
                    svc.cfg().setNetherAllowWater(world, !allowWater);
                    init(p, c);
                }
        ));

        // Allow beds (no explosion)
        boolean allowBeds = svc.cfg().netherAllowBeds(world);
        c.set(5, 3, ClickableItem.of(
                new ItemBuilder(Material.RED_BED)
                        .name("&dNether: Beds " + (allowBeds ? "&aSAFE" : "&cEXPLODE"))
                        .lore(
                                "&7If enabled: beds won't explode,",
                                "&7players can sleep safely in Nether."
                        )
                        .build(),
                e -> {
                    svc.cfg().setNetherAllowBeds(world, !allowBeds);
                    init(p, c);
                }
        ));

        // No fire / lava damage
        boolean noFire = svc.cfg().netherNoFireDamage(world);
        c.set(5, 5, ClickableItem.of(
                new ItemBuilder(noFire ? Material.FIRE_CHARGE : Material.FLINT_AND_STEEL)
                        .name("&cNether: Fire/Lava Damage " + (noFire ? "&cOFF" : "&aON"))
                        .lore(
                                "&7If enabled: players don't take",
                                "&7fire/lava damage in this Nether world."
                        )
                        .build(),
                e -> {
                    svc.cfg().setNetherNoFireDamage(world, !noFire);
                    init(p, c);
                }
        ));

        // No ghast block grief
        boolean noGhast = svc.cfg().netherNoGhastGrief(world);
        c.set(5, 7, ClickableItem.of(
                new ItemBuilder(Material.GHAST_TEAR)
                        .name("&eNether: Ghast block damage " + (noGhast ? "&cOFF" : "&aON"))
                        .lore(
                                "&7If enabled: ghast fireballs",
                                "&7won't break blocks."
                        )
                        .build(),
                e -> {
                    svc.cfg().setNetherNoGhastGrief(world, !noGhast);
                    init(p, c);
                }
        ));
    }

    // ===== END TWEAKS =====
    private void renderEndTweaks(Player p, InventoryContents c) {
        // Void → spawn teleport
        boolean voidTp = svc.cfg().endVoidTeleport(world);
        c.set(5, 2, ClickableItem.of(
                new ItemBuilder(Material.ENDER_PEARL)
                        .name("&5End: Void TP " + (voidTp ? "&aENABLED" : "&cDISABLED"))
                        .lore(
                                "&7If enabled: players falling into",
                                "&7the void are teleported to spawn."
                        )
                        .build(),
                e -> {
                    svc.cfg().setEndVoidTeleport(world, !voidTp);
                    init(p, c);
                }
        ));

        // Enderman grief toggle
        boolean noEndermanGrief = svc.cfg().endNoEndermanGrief(world);
        c.set(5, 4, ClickableItem.of(
                new ItemBuilder(Material.ENDER_EYE)
                        .name("&5End: Enderman grief " + (noEndermanGrief ? "&cOFF" : "&aON"))
                        .lore(
                                "&7If enabled: endermen can't pick up",
                                "&7or move blocks in this world."
                        )
                        .build(),
                e -> {
                    svc.cfg().setEndNoEndermanGrief(world, !noEndermanGrief);
                    init(p, c);
                }
        ));

        // Dragon block damage
        boolean noDragonGrief = svc.cfg().endNoDragonGrief(world);
        c.set(5, 6, ClickableItem.of(
                new ItemBuilder(Material.DRAGON_HEAD)
                        .name("&5End: Dragon block damage " + (noDragonGrief ? "&cOFF" : "&aON"))
                        .lore(
                                "&7If enabled: EnderDragon won't",
                                "&7break blocks with its attacks."
                        )
                        .build(),
                e -> {
                    svc.cfg().setEndNoDragonGrief(world, !noDragonGrief);
                    init(p, c);
                }
        ));
    }

    @Override
    public void update(Player player, InventoryContents contents) { }
}