// File: src/main/java/fr/elias/oreoEssentials/modgui/menu/ServerMenu.java
package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class ServerMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;

    // Simple anti-spam for spawn buttons
    private static final Map<UUID, Long> spawnCooldown = new ConcurrentHashMap<>();
    private static final long SPAWN_COOLDOWN_MS = 1500;

    public ServerMenu(OreoEssentials plugin, ModGuiService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        World main = Bukkit.getWorlds().get(0);

        // ===== Difficulty (cycle & show current) =====
        Difficulty curDiff = main.getDifficulty();
        c.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.ANVIL)
                        .name("&cDifficulty: &f" + colorizeDifficulty(curDiff))
                        .lore("&7Click to cycle PEACEFUL → EASY → NORMAL → HARD")
                        .build(),
                e -> {
                    Difficulty next = switch (main.getDifficulty()) {
                        case PEACEFUL -> Difficulty.EASY;
                        case EASY     -> Difficulty.NORMAL;
                        case NORMAL   -> Difficulty.HARD;
                        case HARD     -> Difficulty.PEACEFUL;
                    };
                    Bukkit.getWorlds().forEach(w -> w.setDifficulty(next));
                    Lang.send(p, "modgui.server.difficulty-set",
                            "<yellow>Difficulty set to <gold>%difficulty%</gold></yellow>",
                            Map.of("difficulty", next.name()));
                    // Refresh labels
                    init(p, c);
                }
        ));

        // ===== Default GM (cycle & show current) =====
        GameMode curGm = Bukkit.getDefaultGameMode();
        c.set(1, 5, ClickableItem.of(
                new ItemBuilder(Material.NETHER_STAR)
                        .name("&dDefault Gamemode: &f" + curGm)
                        .lore("&7Click to cycle SURVIVAL → CREATIVE → ADVENTURE → SPECTATOR")
                        .build(),
                e -> {
                    GameMode next = switch (Bukkit.getDefaultGameMode()) {
                        case SURVIVAL  -> GameMode.CREATIVE;
                        case CREATIVE  -> GameMode.ADVENTURE;
                        case ADVENTURE -> GameMode.SPECTATOR;
                        case SPECTATOR -> GameMode.SURVIVAL;
                    };
                    Bukkit.setDefaultGameMode(next);
                    Lang.send(p, "modgui.server.gamemode-set",
                            "<yellow>Default gamemode is now <gold>%gamemode%</gold></yellow>",
                            Map.of("gamemode", next.name()));
                    init(p, c);
                }
        ));

        // ===== Whitelist toggle (live state) =====
        boolean wl = Bukkit.hasWhitelist();
        c.set(1, 7, ClickableItem.of(
                new ItemBuilder(wl ? Material.LIME_DYE : Material.GRAY_DYE)
                        .name("&fServer whitelist: " + (wl ? "&aENABLED" : "&cDISABLED"))
                        .lore("&7Click to " + (wl ? "disable" : "enable"))
                        .build(),
                e -> {
                    Bukkit.setWhitelist(!Bukkit.hasWhitelist());
                    boolean enabled = Bukkit.hasWhitelist();
                    Lang.send(p, "modgui.server.whitelist-toggled",
                            "<yellow>Whitelist is now %state%</yellow>",
                            Map.of("state", enabled ? "<green>ENABLED</green>" : "<red>DISABLED</red>"));
                    init(p, c);
                }
        ));

        // ===== Quick spawns near you (respect Peaceful) =====
        c.set(3, 3, ClickableItem.of(
                new ItemBuilder(Material.ZOMBIE_HEAD)
                        .name("&2Spawn: 5 Zombies")
                        .build(),
                e -> spawn(p, c, EntityType.ZOMBIE, 5)
        ));

        c.set(3, 4, ClickableItem.of(
                new ItemBuilder(Material.SKELETON_SKULL)
                        .name("&7Spawn: 5 Skeletons")
                        .build(),
                e -> spawn(p, c, EntityType.SKELETON, 5)
        ));

        c.set(3, 5, ClickableItem.of(
                new ItemBuilder(Material.CREEPER_HEAD)
                        .name("&aSpawn: 3 Creepers")
                        .build(),
                e -> spawn(p, c, EntityType.CREEPER, 3)
        ));

        c.set(3, 6, ClickableItem.of(
                new ItemBuilder(Material.BLAZE_ROD)
                        .name("&6Spawn: 3 Blazes")
                        .build(),
                e -> spawn(p, c, EntityType.BLAZE, 3)
        ));

        // ===== Performance / TPS dashboard =====
        c.set(0, 4, ClickableItem.of(
                new ItemBuilder(Material.COMPARATOR)
                        .name("&cTPS & Performance")
                        .lore("&7View TPS per world, CPU and RAM.")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new TpsDashboardMenu(plugin))
                        .title(Lang.color(Lang.get("modgui.server.tps-title", "&8TPS & Performance")))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        // ===== Chat moderation =====
        c.set(4, 1, ClickableItem.of(
                new ItemBuilder(Material.OAK_SIGN)
                        .name("&eChat moderation")
                        .lore("&7Mute, clear, slowmode, staff chat...")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new ChatModerationMenu(plugin))
                        .title(Lang.color(Lang.get("modgui.server.chat-title", "&8Chat moderation")))
                        .size(3, 9)
                        .build()
                        .open(p)
        ));

        // ===== Perf tools =====
        c.set(4, 7, ClickableItem.of(
                new ItemBuilder(Material.LAVA_BUCKET)
                        .name("&6Performance tools")
                        .lore("&7Kill mobs/items, purge TNT, etc.")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new PerfToolsMenu(plugin))
                        .title(Lang.color(Lang.get("modgui.server.perf-title", "&8Performance tools")))
                        .size(3, 9)
                        .build()
                        .open(p)
        ));
    }

    private String colorizeDifficulty(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> "§aPEACEFUL";
            case EASY     -> "§2EASY";
            case NORMAL   -> "§eNORMAL";
            case HARD     -> "§cHARD";
        };
    }

    private void spawn(Player p, InventoryContents c, EntityType type, int count) {
        // Cooldown check
        long now = System.currentTimeMillis();
        long last = spawnCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < SPAWN_COOLDOWN_MS) {
            Lang.send(p, "modgui.server.spawn-cooldown",
                    "<red>Please wait a second before spawning again.</red>",
                    Map.of());
            return;
        }

        // Respect Peaceful difficulty
        if (p.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            Lang.send(p, "modgui.server.spawn-peaceful",
                    "<red>Cannot spawn hostile mobs in <green>PEACEFUL</green> difficulty.</red>",
                    Map.of());
            return;
        }

        spawnCooldown.put(p.getUniqueId(), now);
        for (int i = 0; i < count; i++) {
            p.getWorld().spawnEntity(p.getLocation(), type);
        }

        Lang.send(p, "modgui.server.spawned",
                "<green>Spawned <yellow>%count%</yellow> <gray>%type%(s)</gray>.</green>",
                Map.of(
                        "count", String.valueOf(count),
                        "type", type.name().toLowerCase()
                ));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}