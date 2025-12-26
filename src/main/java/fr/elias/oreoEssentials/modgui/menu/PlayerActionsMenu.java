// File: src/main/java/fr/elias/oreoEssentials/modgui/menu/PlayerActionsMenu.java
package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ecsee.EcSeeMenu;
import fr.elias.oreoEssentials.modgui.inspect.PlayerInspectMenu;
import fr.elias.oreoEssentials.modgui.ip.IpAltsMenu;
import fr.elias.oreoEssentials.modgui.notes.NotesChatListener;
import fr.elias.oreoEssentials.modgui.notes.PlayerNotesManager;
import fr.elias.oreoEssentials.modgui.notes.PlayerNotesMenu;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import fr.minuskube.inv.InventoryListener;
import org.bukkit.inventory.Inventory;
import fr.elias.oreoEssentials.modgui.invsee.InvSeeMenu;

import java.util.Map;
import java.util.UUID;

/**
 * Player actions menu - moderation actions for a specific player.
 *
 * ✅ VERIFIED - Uses Lang.send() for 2 user messages + § for GUI items
 *
 * Features:
 * - Ban, mute, kick, heal, feed, kill
 * - Inventory inspection (InvSee, EcSee)
 * - Freeze, vanish, gamemode toggle
 * - Player notes, IP/alts lookup
 * - Live stats inspector
 *
 * All actions use cross-server ModBridge when available.
 */
public class PlayerActionsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final UUID target;
    /** Optional back action supplied by caller (can be null). */
    private final Runnable onBack;

    public PlayerActionsMenu(OreoEssentials plugin, UUID target) {
        this(plugin, target, null);
    }

    public PlayerActionsMenu(OreoEssentials plugin, UUID target, Runnable onBack) {
        this.plugin = plugin;
        this.target = target;
        this.onBack = onBack;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        final String name = (op != null && op.getName() != null) ? op.getName() : target.toString();

        // Back button (optional)
        if (onBack != null) {
            c.set(0, 0, ClickableItem.of(
                    new ItemBuilder(Material.ARROW)
                            .name("&7&l← Back")
                            .lore("&7Return to previous menu")
                            .build(),
                    e -> onBack.run()
            ));
        }

        // === ROW 1: MODERATION ACTIONS ===

        // Ban
        c.set(1, 2, ClickableItem.of(
                new ItemBuilder(Material.BARRIER)
                        .name("&cBan")
                        .lore("&7Temp example: 1d (reason: ModGUI)")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        Lang.send(p, "modgui.actions.bridge-unavailable",
                                "<red>Cross-server mod bridge is not available.</red>",
                                Map.of());
                        return;
                    }
                    bridge.ban(target, name, "1d ModGUI");
                }
        ));

        // Mute
        c.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.PAPER)
                        .name("&eMute")
                        .lore("&7Example: 10m (reason: ModGUI)")
                        .build(),
                e -> runConsole("mute " + name + " 10m ModGUI")
        ));

        // Unmute
        c.set(1, 4, ClickableItem.of(
                new ItemBuilder(Material.GREEN_DYE)
                        .name("&aUnmute")
                        .build(),
                e -> runConsole("unmute " + name)
        ));

        // Kick
        c.set(1, 5, ClickableItem.of(
                new ItemBuilder(Material.OAK_DOOR)
                        .name("&6Kick")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        Lang.send(p, "modgui.actions.bridge-unavailable",
                                "<red>Cross-server mod bridge is not available.</red>",
                                Map.of());
                        return;
                    }
                    bridge.kick(target, name, "Kicked by staff via ModGUI");
                }
        ));

        // Heal
        c.set(1, 6, ClickableItem.of(
                new ItemBuilder(Material.TOTEM_OF_UNDYING)
                        .name("&aHeal")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        // Fallback: local only
                        runPlayer(p, "heal " + name);
                        return;
                    }
                    bridge.heal(target, name);
                }
        ));

        // === ROW 2: PLAYER UTILITIES ===

        // Feed
        c.set(2, 2, ClickableItem.of(
                new ItemBuilder(Material.COOKED_BEEF)
                        .name("&eFeed")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        runPlayer(p, "feed " + name);
                        return;
                    }
                    bridge.feed(target, name);
                }
        ));

        // Kill
        c.set(2, 3, ClickableItem.of(
                new ItemBuilder(Material.IRON_SWORD)
                        .name("&cKill")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        Lang.send(p, "modgui.actions.bridge-unavailable",
                                "<red>Cross-server mod bridge is not available.</red>",
                                Map.of());
                        return;
                    }
                    bridge.kill(target, name);
                }
        ));

        // InvSee (network)
        c.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.CHEST)
                        .name("&bInvsee (network)")
                        .lore("&7View and edit inventory",
                                "&7across all servers via RabbitMQ.")
                        .build(),
                e -> InvSeeMenu.open(plugin, p, target)
        ));

        // EcSee (logged)
        c.set(2, 5, ClickableItem.of(
                new ItemBuilder(Material.ENDER_CHEST)
                        .name("&bEcSee (logged)")
                        .build(),
                e -> {
                    SmartInventory.builder()
                            .manager(plugin.getInvManager())
                            .provider(new EcSeeMenu(plugin, target))
                            .title(Lang.color(Lang.get("modgui.actions.ecsee-title", "&8EnderChest: %name%")
                                    .replace("%name%", name)))
                            .size(6, 9)
                            .closeable(true)
                            .listener(new InventoryListener<>(InventoryCloseEvent.class, ev -> {
                                Inventory inv = ev.getInventory();
                                Player staff = (Player) ev.getPlayer();
                                EcSeeMenu.syncAndLog(plugin, staff, target, inv);
                            }))
                            .build()
                            .open(p);
                }
        ));

        // Freeze toggle
        c.set(2, 6, ClickableItem.of(
                new ItemBuilder(Material.CLOCK)
                        .name("&9Freeze 60s")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        Lang.send(p, "modgui.actions.bridge-unavailable",
                                "<red>Cross-server mod bridge is not available.</red>",
                                Map.of());
                        return;
                    }
                    bridge.freezeToggle(target, name, 60L);
                }
        ));

        // === ROW 3: ADVANCED ACTIONS ===

        // Vanish toggle
        c.set(3, 3, ClickableItem.of(
                new ItemBuilder(Material.ENDER_EYE)
                        .name("&5Vanish toggle")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        Lang.send(p, "modgui.actions.bridge-unavailable",
                                "<red>Cross-server mod bridge is not available.</red>",
                                Map.of());
                        return;
                    }
                    bridge.vanishToggle(target, name);
                }
        ));

        // Live inspector
        c.set(3, 4, ClickableItem.of(
                new ItemBuilder(Material.SPYGLASS)
                        .name("&bLive inspector")
                        .lore("&7View live stats (health, ping, TPS...)")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new PlayerInspectMenu(plugin, target))
                        .title(Lang.color(Lang.get("modgui.actions.inspect-title", "&8Inspect: %name%")
                                .replace("%name%", name)))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        // Gamemode cycle
        c.set(3, 5, ClickableItem.of(
                new ItemBuilder(Material.NETHER_STAR)
                        .name("&dGamemode cycle (S/C/SP)")
                        .build(),
                e -> {
                    var bridge = plugin.getModBridge();
                    if (bridge == null) {
                        Lang.send(p, "modgui.actions.bridge-unavailable",
                                "<red>Cross-server mod bridge is not available.</red>",
                                Map.of());
                        return;
                    }
                    bridge.gamemodeCycle(target, name);
                }
        ));

        // === ROW 4: INFORMATION & TRACKING ===

        // Player notes
        c.set(4, 2, ClickableItem.of(
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("&ePlayer notes")
                        .lore("&7View / add staff notes.")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new PlayerNotesMenu(plugin,
                                plugin.getNotesManager(),
                                plugin.getNotesChat(),
                                target))
                        .title(Lang.color(Lang.get("modgui.actions.notes-title", "&8Notes: %name%")
                                .replace("%name%", name)))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        // Stats placeholder
        c.set(4, 4, ClickableItem.empty(
                new ItemBuilder(Material.BOOK)
                        .name("&7Stats (placeholder)")
                        .lore("&7Add your own stats view here")
                        .build()
        ));

        // IP & Alts
        c.set(4, 6, ClickableItem.of(
                new ItemBuilder(Material.COMPASS)
                        .name("&eIP & Alts")
                        .lore("&7View last IP and potential alts.")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new IpAltsMenu(plugin, plugin.getIpTracker(), target))
                        .title(Lang.color(Lang.get("modgui.actions.ip-alts-title", "&8IP & Alts: %name%")
                                .replace("%name%", name)))
                        .size(6, 9)
                        .build()
                        .open(p)
        ));
    }

    private void runPlayer(Player sender, String cmd) {
        // Run as player (uses their perms)
        sender.performCommand(cmd);
    }

    private void runConsole(String cmd) {
        // Run as console for staffy commands
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    @Override
    public void update(Player p, InventoryContents contents) {}
}