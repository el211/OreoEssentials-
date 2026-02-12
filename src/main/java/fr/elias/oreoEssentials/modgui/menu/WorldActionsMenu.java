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

public class WorldActionsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final World world;

    public WorldActionsMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin = plugin;
        this.svc = svc;
        this.world = world;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        setupTimeButtons(p, c);
        setupWeatherButtons(p, c);
        setupSpawnButton(p, c);
        setupBorderButtons(p, c);
        setupSubMenuButtons(p, c);
        setupToggleButtons(p, c);
        setupDimensionSpecificTweaks(p, c);
    }

    private void setupTimeButtons(Player p, InventoryContents c) {
        c.set(1, 2, createTimeButton(p, c, "Day", 1000, Material.CLOCK, "&e"));
        c.set(1, 3, createTimeButton(p, c, "Sunset", 12000, Material.CLOCK, "&6"));
        c.set(1, 4, createTimeButton(p, c, "Night", 18000, Material.CLOCK, "&c"));
    }

    private ClickableItem createTimeButton(Player p, InventoryContents c, String label, long ticks, Material material, String color) {
        return ClickableItem.of(
                new ItemBuilder(material)
                        .name(color + "Time: " + label)
                        .lore("&7Now: " + world.getTime())
                        .build(),
                e -> {
                    setTime(ticks);
                    Lang.send(p, "modgui.world-actions.time-set",
                            "<yellow>Time set to <gold>%time%</gold></yellow>",
                            Map.of("time", label));
                    init(p, c);
                }
        );
    }

    private void setupWeatherButtons(Player p, InventoryContents c) {
        c.set(2, 2, createWeatherButton(p, c, "Clear", "sun", Material.SUNFLOWER, "&e"));
        c.set(2, 3, createWeatherButton(p, c, "Rain", "rain", Material.WATER_BUCKET, "&b"));
        c.set(2, 4, createWeatherButton(p, c, "Storm", "storm", Material.TRIDENT, "&3"));
    }

    private ClickableItem createWeatherButton(Player p, InventoryContents c, String label, String mode, Material material, String color) {
        return ClickableItem.of(
                new ItemBuilder(material)
                        .name(color + "Weather: " + label)
                        .lore(getCurrentWeatherLore())
                        .build(),
                e -> {
                    setWeather(mode);
                    Lang.send(p, "modgui.world-actions.weather-set",
                            "<yellow>Weather set to <gold>%weather%</gold></yellow>",
                            Map.of("weather", label));
                    init(p, c);
                }
        );
    }

    private void setupSpawnButton(Player p, InventoryContents c) {
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
    }

    private void setupBorderButtons(Player p, InventoryContents c) {
        c.set(3, 4, createBorderButton(p, c, "1k", 1000, "1,000"));
        c.set(3, 5, createBorderButton(p, c, "2k", 2000, "2,000"));
        c.set(3, 6, createBorderButton(p, c, "5k", 5000, "5,000"));
    }

    private ClickableItem createBorderButton(Player p, InventoryContents c, String label, int size, String displaySize) {
        return ClickableItem.of(
                new ItemBuilder(Material.IRON_BARS)
                        .name("&fBorder: " + label)
                        .lore(getBorderLore())
                        .build(),
                e -> {
                    setBorder(size);
                    Lang.send(p, "modgui.world-actions.border-set",
                            "<yellow>World border set to <gold>%size%</gold></yellow>",
                            Map.of("size", displaySize));
                    init(p, c);
                }
        );
    }

    private void setupSubMenuButtons(Player p, InventoryContents c) {
        c.set(4, 3, createSubMenuButton(p, "Gamerules (per-world)", Material.BOOK,
                new WorldGamerulesMenu(plugin, svc, world),
                "modgui.world-actions.gamerules-title", "&8Gamerules: %world%"));

        c.set(4, 5, createSubMenuButton(p, "Whitelist (per-world)", Material.WHITE_WOOL,
                new WorldWhitelistMenu(plugin, svc, world),
                "modgui.world-actions.whitelist-title", "&8Whitelist: %world%"));

        c.set(4, 7, createSubMenuButton(p, "Banned mobs (per-world)", Material.ZOMBIE_HEAD,
                new WorldBannedMobsMenu(plugin, svc, world),
                "modgui.world-actions.banned-mobs-title", "&8Banned Mobs: %world%"));
    }

    private ClickableItem createSubMenuButton(Player p, String name, Material material,
                                              InventoryProvider provider, String titleKey, String defaultTitle) {
        return ClickableItem.of(
                new ItemBuilder(material)
                        .name("&b" + name)
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(provider)
                        .title(Lang.color(Lang.get(titleKey, defaultTitle)
                                .replace("%world%", world.getName())))
                        .size(6, 9)
                        .build()
                        .open(p)
        );
    }

    private void setupToggleButtons(Player p, InventoryContents c) {
        setupElytraToggle(p, c);
        setupTridentToggle(p, c);
        setupPvpToggle(p, c);
        setupProjectilePvpToggle(p, c);
        setupThemeCycle(p, c);
    }

    private void setupElytraToggle(Player p, InventoryContents c) {
        boolean disabled = svc.cfg().disableElytra(world);
        c.set(5, 1, ClickableItem.of(
                new ItemBuilder(Material.ELYTRA)
                        .name("&bElytra in this world: " + (disabled ? "&cDISABLED" : "&aALLOWED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setDisableElytra(world, !disabled);
                    init(p, c);
                }
        ));
    }

    private void setupTridentToggle(Player p, InventoryContents c) {
        boolean disabled = svc.cfg().disableTrident(world);
        c.set(5, 2, ClickableItem.of(
                new ItemBuilder(Material.TRIDENT)
                        .name("&bTridents: " + (disabled ? "&cDISABLED" : "&aALLOWED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setDisableTrident(world, !disabled);
                    init(p, c);
                }
        ));
    }

    private void setupPvpToggle(Player p, InventoryContents c) {
        boolean enabled = svc.cfg().pvpEnabled(world);
        c.set(5, 4, ClickableItem.of(
                new ItemBuilder(Material.DIAMOND_SWORD)
                        .name("&cPVP: " + (enabled ? "&aENABLED" : "&cDISABLED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setPvpEnabled(world, !enabled);
                    world.setPVP(!enabled);
                    init(p, c);
                }
        ));
    }

    private void setupProjectilePvpToggle(Player p, InventoryContents c) {
        boolean disabled = svc.cfg().disableProjectilePvp(world);
        c.set(5, 5, ClickableItem.of(
                new ItemBuilder(Material.ARROW)
                        .name("&cProjectile PVP: " + (disabled ? "&cDISABLED" : "&aENABLED"))
                        .lore("&7Click to toggle.")
                        .build(),
                e -> {
                    svc.cfg().setDisableProjectilePvp(world, !disabled);
                    init(p, c);
                }
        ));
    }

    private void setupThemeCycle(Player p, InventoryContents c) {
        String theme = svc.cfg().worldTheme(world);
        c.set(5, 7, ClickableItem.of(
                new ItemBuilder(Material.GLOWSTONE_DUST)
                        .name("&dWorld theme: &f" + theme)
                        .lore("&7Click to cycle: DEFAULT → RED → PURPLE → GREEN → BLUE")
                        .build(),
                e -> {
                    String next = getNextTheme(theme);
                    svc.cfg().setWorldTheme(world, next);
                    init(p, c);
                }
        ));
    }

    private void setupDimensionSpecificTweaks(Player p, InventoryContents c) {
        switch (world.getEnvironment()) {
            case NETHER -> setupNetherTweaks(p, c);
            case THE_END -> setupEndTweaks(p, c);
            default -> {}
        }
    }

    private void setupNetherTweaks(Player p, InventoryContents c) {
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

    private void setupEndTweaks(Player p, InventoryContents c) {
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

    private String getCurrentWeatherLore() {
        boolean storm = world.hasStorm();
        boolean thunder = world.isThundering();
        String state = (!storm && !thunder) ? "§eClear" : (storm && !thunder) ? "§bRain" : "§3Storm";
        return "§7Current: " + state;
    }

    private String getBorderLore() {
        int size = (int) world.getWorldBorder().getSize();
        return "§7Current: §f" + size;
    }

    private void setTime(long ticks) {
        world.setTime(ticks);
    }

    private void setWeather(String mode) {
        switch (mode) {
            case "sun" -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "storm" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
        }
    }

    private void setBorder(int size) {
        var wb = world.getWorldBorder();
        if (wb.getCenter().getWorld() == null || !wb.getCenter().getWorld().equals(world)) {
            wb.setCenter(world.getSpawnLocation());
        }
        wb.setSize(size);
    }

    private String getNextTheme(String current) {
        return switch (current.toUpperCase()) {
            case "DEFAULT" -> "RED";
            case "RED" -> "PURPLE";
            case "PURPLE" -> "GREEN";
            case "GREEN" -> "BLUE";
            default -> "DEFAULT";
        };
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}