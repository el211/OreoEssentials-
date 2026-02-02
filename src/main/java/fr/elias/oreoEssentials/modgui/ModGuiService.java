package fr.elias.oreoEssentials.modgui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.cfg.ModGuiConfig;
import fr.elias.oreoEssentials.modgui.menu.MainMenu;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class ModGuiService implements TabExecutor {
    private final OreoEssentials plugin;
    private final InventoryManager inv;
    private final ModGuiConfig config;

    public ModGuiService(OreoEssentials plugin) {
        this.plugin = plugin;
        this.inv = plugin.getInvManager();

        this.config = new ModGuiConfig(plugin);
        this.config.load();

        if (plugin.getCommand("modgui") != null) {
            plugin.getCommand("modgui").setExecutor(this);
            plugin.getCommand("modgui").setTabCompleter(this);
        }
    }

    public ModGuiConfig cfg() { return config; }

    public void openMain(Player p) {
        SmartInventory.builder()
                .manager(inv)
                .id("modgui-main")
                .provider(new MainMenu(plugin, this))
                .size(6, 9)
                .title(Lang.color(Lang.get("modgui.main.title", "&8Moderation Panel")))
                .build()
                .open(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Lang.send(sender, "modgui.player-only",
                    "<red>Players only.</red>",
                    Map.of());
            return true;
        }
        if (!p.hasPermission("oreo.modgui.open")) {
            Lang.send(p, "modgui.no-permission",
                    "<red>No permission: <white>oreo.modgui.open</white></red>",
                    Map.of());
            return true;
        }
        openMain(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private boolean chatMuted = false;
    private int slowmodeSeconds = 0;
    private final Map<UUID, Boolean> staffChat = new HashMap<>();
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public boolean chatMuted() { return chatMuted; }
    public void setChatMuted(boolean b) { chatMuted = b; }

    public int getSlowmodeSeconds() { return slowmodeSeconds; }
    public void setSlowmodeSeconds(int sec) { slowmodeSeconds = sec; }

    public boolean isStaffChatEnabled(UUID id) {
        return staffChat.getOrDefault(id, false);
    }

    public void setStaffChatEnabled(UUID id, boolean enabled) {
        staffChat.put(id, enabled);
    }

    public boolean canSendMessage(UUID id) {
        int slow = slowmodeSeconds;
        if (slow <= 0) return true;
        long last = lastMessage.getOrDefault(id, 0L);
        return (System.currentTimeMillis() - last) >= slow * 1000L;
    }

    public long getRemainingSlowmode(UUID id) {
        long last = lastMessage.getOrDefault(id, 0L);
        long diff = System.currentTimeMillis() - last;
        long wait = slowmodeSeconds * 1000L - diff;
        return Math.max(wait / 1000L, 0);
    }

    public void recordMessage(UUID id) {
        lastMessage.put(id, System.currentTimeMillis());
    }

    public void save() {
        try { config.save(); } catch (Exception ignored) {}
    }
}