package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.modules.kits.KitsManager;
import fr.elias.oreoEssentials.modules.warps.WarpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class OreoDialogManager {

    private final OreoEssentials plugin;
    private final boolean apiAvailable;

    public OreoDialogManager(OreoEssentials plugin) {
        this.plugin = plugin;
        boolean available = false;
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            available = true;
        } catch (ClassNotFoundException ignored) {}
        this.apiAvailable = available;
        if (available) plugin.getLogger().info("[Dialog] Paper Dialog API detected — dialog support enabled.");
        else plugin.getLogger().warning("[Dialog] Paper Dialog API not found (requires Paper 1.21.5+). Dialog UI disabled.");
    }

    public boolean isAvailable() { return apiAvailable; }

    public boolean isEnabled() {
        return apiAvailable && plugin.getSettingsConfig().isDialogEnabled();
    }

    public boolean isEnabledFor(String feature) {
        return apiAvailable && plugin.getSettingsConfig().isDialogEnabledFor(feature);
    }

    /** Open the home list/selection dialog. */
    public boolean openHomeDialog(Player player, HomeService homes) {
        if (!isEnabledFor("homes")) return false;
        try {
            HomeDialogHandler.open(plugin, player, homes);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] Home dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Open the warp list dialog. */
    public boolean openWarpDialog(Player player, WarpService warps) {
        if (!isEnabledFor("warps")) return false;
        try {
            WarpDialogHandler.open(plugin, player, warps);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] Warp dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Send a TPA request dialog (Accept/Deny) to the target player. */
    public boolean sendTpaRequestDialog(Player target, Player requester, boolean isHere) {
        if (!isEnabledFor("tpa")) return false;
        try {
            TpaDialogHandler.sendRequest(plugin, target, requester, isHere);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] TPA dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Open the player warp browse dialog. */
    public boolean openPlayerWarpDialog(Player player) {
        if (!isEnabledFor("player-warps")) return false;
        try {
            PlayerWarpDialogHandler.open(plugin, player);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] PlayerWarp dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Open the kits selection dialog. */
    public boolean openKitsDialog(Player player, KitsManager manager) {
        if (!isEnabledFor("kits")) return false;
        try {
            KitsDialogHandler.open(plugin, manager, player);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] Kits dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Open the nearby players dialog. */
    public boolean openNearDialog(Player player, int radius) {
        if (!isEnabledFor("near")) return false;
        try {
            NearDialogHandler.open(plugin, player, radius);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] Near dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Open the chat channels selection dialog. */
    public boolean openChannelsDialog(Player player, ChatChannelManager channelManager) {
        if (!isEnabledFor("channels")) return false;
        try {
            ChannelsDialogHandler.open(plugin, player, channelManager);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] Channels dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Show a confirm dialog before deleting a home. Confirm runs /delhome <name> --confirmed. */
    public boolean confirmDelHome(Player player, String homeName) {
        if (!isEnabledFor("confirm-delhome")) return false;
        try {
            ConfirmDialogHandler.open(plugin, player,
                    Component.text("Delete Home", NamedTextColor.RED),
                    Component.text("Delete home \u201c" + homeName + "\u201d? This cannot be undone.", NamedTextColor.GRAY),
                    "delhome " + homeName + " --confirmed"
            );
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] DelHome confirm dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Show a confirm dialog before deleting a warp. Confirm runs /delwarp <name> --confirmed. */
    public boolean confirmDelWarp(Player player, String warpName) {
        if (!isEnabledFor("confirm-delwarp")) return false;
        try {
            ConfirmDialogHandler.open(plugin, player,
                    Component.text("Delete Warp", NamedTextColor.RED),
                    Component.text("Delete warp \u201c" + warpName + "\u201d? This cannot be undone.", NamedTextColor.GRAY),
                    "delwarp " + warpName + " --confirmed"
            );
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] DelWarp confirm dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Show a confirm dialog before clearing the mailbox. Confirm runs /mail clear --confirmed. */
    public boolean confirmMailClear(Player player) {
        if (!isEnabledFor("confirm-mailclear")) return false;
        try {
            ConfirmDialogHandler.open(plugin, player,
                    Component.text("Clear Mailbox", NamedTextColor.RED),
                    Component.text("Delete all your mail messages? This cannot be undone.", NamedTextColor.GRAY),
                    "mail clear --confirmed"
            );
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] MailClear confirm dialog failed: " + t.getMessage());
            return false;
        }
    }

    /** Show a confirm dialog before removing a player warp. Confirm runs /pw remove <name> --confirmed. */
    public boolean confirmPlayerWarpRemove(Player player, String warpName) {
        if (!isEnabledFor("confirm-pwremove")) return false;
        try {
            ConfirmDialogHandler.open(plugin, player,
                    Component.text("Remove Player Warp", NamedTextColor.RED),
                    Component.text("Remove your player warp \u201c" + warpName + "\u201d?", NamedTextColor.GRAY),
                    "pw remove " + warpName + " --confirmed"
            );
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dialog] PlayerWarp remove confirm failed: " + t.getMessage());
            return false;
        }
    }
}
