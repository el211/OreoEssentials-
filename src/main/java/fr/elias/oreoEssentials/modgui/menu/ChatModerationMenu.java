package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;


public class ChatModerationMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final MuteService mute;
    private final ChatSyncManager sync; // may be null if network-chat disabled

    public ChatModerationMenu(OreoEssentials plugin) {
        this.plugin = plugin;
        this.svc   = plugin.getModGuiService();
        this.mute  = plugin.getMuteService();
        this.sync  = plugin.getChatSyncManager(); // you should already have this getter
    }

    @Override
    public void init(Player p, InventoryContents c) {

        /* ============= 1. CLEAR CHAT (NETWORK) ============= */
        c.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.BARRIER)
                        .name("&cClear Chat")
                        .lore("&7Clear chat on this server",
                                "&7and broadcast to the network.")
                        .build(),
                e -> clearChatNetwork(p)
        ));

        /* ============= 2. GLOBAL CHAT MUTE (NETWORK) ============= */
        boolean chatMuted = svc != null && svc.chatMuted();
        c.set(1, 5, ClickableItem.of(
                new ItemBuilder(chatMuted ? Material.RED_DYE : Material.LIME_DYE)
                        .name("&eGlobal Chat: " + (chatMuted ? "&cMUTED" : "&aENABLED"))
                        .lore("&7Click to toggle global chat mute",
                                "&7(affects all servers using RabbitMQ).")
                        .build(),
                e -> toggleGlobalMuteNetwork(p, !chatMuted, c)
        ));

        /* ============= 3. SLOWMODE (NETWORK) ============= */
        int slow = (svc != null) ? svc.getSlowmodeSeconds() : 0;
        c.set(2, 3, ClickableItem.of(
                new ItemBuilder(Material.CLOCK)
                        .name("&eSlowmode (network)")
                        .lore("Current: &b" + slow + "s",
                                "",
                                "&7Left-click: +1s",
                                "&7Right-click: -1s",
                                "&7Shift-left: +5s",
                                "&7Shift-right: -5s",
                                "",
                                "&7Syncs to all servers via RabbitMQ.")
                        .build(),
                e -> adjustSlowmodeNetwork(p, e.isLeftClick(), e.isRightClick(), e.isShiftClick(), c)
        ));

        /* ============= 4. STAFF CHAT TOGGLE (LOCAL PER PLAYER) ============= */
        boolean staff = svc != null && svc.isStaffChatEnabled(p.getUniqueId());
        c.set(2, 5, ClickableItem.of(
                new ItemBuilder(staff ? Material.WRITABLE_BOOK : Material.BOOK)
                        .name("&bStaff Chat: " + (staff ? "&aENABLED" : "&cDISABLED"))
                        .lore("&7Toggle staff-only chat mode.",
                                "&7(Per-player, local to this server.)")
                        .build(),
                e -> {
                    if (svc == null) {
                        p.sendMessage("§cModGUI service unavailable.");
                        return;
                    }
                    svc.setStaffChatEnabled(p.getUniqueId(), !staff);
                    p.sendMessage(staff
                            ? "§cStaff chat disabled."
                            : "§aStaff chat enabled.");
                    init(p, c);
                }
        ));

        /* ============= 5. PLAYER MUTE PICKER (USES /MUTE, ALREADY NETWORKED) ============= */
        c.set(4, 4, ClickableItem.of(
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("&6Mute a Player")
                        .lore("&7Open a list of online players to mute.",
                                "&7/!\\ Uses /mute which is already cross-server.")
                        .build(),
                e -> openMuteSelector(p)
        ));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // no live animation for now
    }

    /* =======================================================================
       NETWORK HELPERS
       ======================================================================= */

    /** Clear chat locally AND broadcast a control message to other servers. */
    private void clearChatNetwork(Player actor) {
        // local clear
        for (int i = 0; i < 200; i++) {
            Bukkit.broadcastMessage("");
        }
        Bukkit.broadcastMessage("§cChat has been cleared by §e" + actor.getName());
        actor.sendMessage("§aChat cleared.");

        // network notification
        if (sync != null) {
            try {
                sync.broadcastChatControl("CLEAR_CHAT", "true", actor.getName());
            } catch (Throwable t) {
                plugin.getLogger().warning("[OreoEssentials] Failed to broadcast CLEAR_CHAT: " + t.getMessage());
            }
        }
    }

    /** Toggle global mute locally + send a RabbitMQ control so all servers apply it. */
    private void toggleGlobalMuteNetwork(Player actor, boolean newState, InventoryContents c) {
        if (svc == null) {
            actor.sendMessage("§cModGUI service unavailable.");
            return;
        }

        svc.setChatMuted(newState);

        if (newState) {
            Bukkit.broadcastMessage("§cGlobal chat has been muted by §e" + actor.getName());
        } else {
            Bukkit.broadcastMessage("§aGlobal chat has been unmuted.");
        }

        // network
        if (sync != null) {
            try {
                sync.broadcastChatControl("GLOBAL_MUTE", Boolean.toString(newState), actor.getName());
            } catch (Throwable t) {
                plugin.getLogger().warning("[OreoEssentials] Failed to broadcast GLOBAL_MUTE: " + t.getMessage());
            }
        }

        // refresh GUI labels
        init(actor, c);
    }

    /** Change slowmode locally + broadcast to all servers. */
    private void adjustSlowmodeNetwork(Player actor,
                                       boolean leftClick,
                                       boolean rightClick,
                                       boolean shift,
                                       InventoryContents c) {
        if (svc == null) {
            actor.sendMessage("§cModGUI service unavailable.");
            return;
        }

        int current = svc.getSlowmodeSeconds();
        int delta = 0;

        if (leftClick) {
            delta = shift ? 5 : 1;
        } else if (rightClick) {
            delta = shift ? -5 : -1;
        }

        if (delta == 0) return;

        int newSlow = Math.max(0, current + delta);
        svc.setSlowmodeSeconds(newSlow);
        actor.sendMessage("§eSlowmode set to §6" + newSlow + "s");

        // network
        if (sync != null) {
            try {
                sync.broadcastChatControl("SLOWMODE", Integer.toString(newSlow), actor.getName());
            } catch (Throwable t) {
                plugin.getLogger().warning("[OreoEssentials] Failed to broadcast SLOWMODE: " + t.getMessage());
            }
        }

        init(actor, c);
    }

    /* =======================================================================
       MUTE PICKER (GUI) – uses /mute which already syncs via RabbitMQ
       ======================================================================= */

    private void openMuteSelector(Player admin) {
        SmartInventory.builder()
                .manager(plugin.getInvManager())
                .title("§8Select player to mute")
                .size(6, 9)
                .provider(new InventoryProvider() {
                    @Override
                    public void init(Player player, InventoryContents contents) {
                        int index = 0;
                        for (Player target : Bukkit.getOnlinePlayers()) {
                            if (index >= 54) break; // 6x9 safety

                            int row = index / 9;
                            int col = index % 9;

                            // Base head item
                            ItemStack base = new ItemBuilder(Material.PLAYER_HEAD)
                                    .name("&e" + target.getName())
                                    .lore("&7Click to mute",
                                            "&7Use /mute " + target.getName() + " <duration> [reason]")
                                    .build();

                            // Apply skull owner
                            if (base.getItemMeta() instanceof SkullMeta meta) {
                                meta.setOwningPlayer(target);
                                base.setItemMeta(meta);
                            }

                            contents.set(row, col, ClickableItem.of(
                                    base,
                                    e -> {
                                        admin.closeInventory();
                                        admin.sendMessage("§eUse: §6/mute " + target.getName() + " 10m §7<reason>");
                                    }
                            ));

                            index++;
                        }
                    }

                    @Override
                    public void update(Player player, InventoryContents contents) {}
                })
                .build()
                .open(admin);
    }
}
