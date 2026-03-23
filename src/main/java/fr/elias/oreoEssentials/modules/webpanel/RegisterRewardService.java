package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.shop.hooks.ItemsAdderHook;
import fr.elias.oreoEssentials.modules.shop.hooks.NexoHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Handles register-reward eligibility checks, claim persistence, and reward delivery.
 * Also listens for player joins to notify unclaimed-but-eligible players.
 */
public class RegisterRewardService implements Listener {

    private final OreoEssentials plugin;
    private final RegisterRewardConfig cfg;
    private final WebPanelClient client;
    private final ItemsAdderHook iaHook;
    private final NexoHook nexoHook;

    /** UUIDs of players who have already claimed the reward. */
    private final Set<UUID> claimed = new HashSet<>();
    private File claimFile;

    public RegisterRewardService(OreoEssentials plugin,
                                  RegisterRewardConfig cfg,
                                  WebPanelClient client,
                                  ItemsAdderHook iaHook,
                                  NexoHook nexoHook) {
        this.plugin   = plugin;
        this.cfg      = cfg;
        this.client   = client;
        this.iaHook   = iaHook;
        this.nexoHook = nexoHook;
        loadClaimed();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadClaimed() {
        File folder = new File(plugin.getDataFolder(), "oreopanel");
        folder.mkdirs();
        claimFile = new File(folder, "reward_claimed.yml");
        if (!claimFile.exists()) return;

        org.bukkit.configuration.file.YamlConfiguration y =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(claimFile);
        for (String s : y.getStringList("claimed")) {
            try { claimed.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
    }

    private void saveClaimed() {
        org.bukkit.configuration.file.YamlConfiguration y =
                new org.bukkit.configuration.file.YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID uuid : claimed) list.add(uuid.toString());
        y.set("claimed", list);
        try {
            y.save(claimFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[RegisterReward] Could not save claimed list: " + e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean hasClaimed(UUID uuid) {
        return claimed.contains(uuid);
    }

    /**
     * Checks asynchronously whether the player's UUID is linked on the website,
     * then invokes {@code callback} on the main thread with the result.
     */
    public void checkRegistered(UUID uuid, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean registered = client.isPlayerRegistered(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(registered));
        });
    }

    /**
     * Delivers all configured rewards to the player and marks them as claimed.
     * Must be called on the main thread.
     */
    public void giveReward(Player player) {
        UUID uuid = player.getUniqueId();
        if (claimed.contains(uuid)) {
            player.sendMessage(cfg.msgAlreadyClaimed());
            return;
        }

        claimed.add(uuid);
        saveClaimed();

        // Money
        if (cfg.moneyEnabled() && cfg.moneyAmount() > 0) {
            Economy eco = plugin.getVaultEconomy();
            if (eco != null) {
                eco.depositPlayer(player, cfg.moneyAmount());
            }
        }

        // Items
        for (Object raw : cfg.itemRewards()) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            ItemStack item = buildItem(map);
            if (item == null) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(o ->
                    player.getWorld().dropItemNaturally(player.getLocation(), o));
        }

        // Commands
        for (Object raw : cfg.commandRewards()) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            Object cmdRaw = map.get("command");
            if (!(cmdRaw instanceof String cmd) || cmd.isBlank()) continue;
            String resolved = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        player.sendMessage(cfg.msgClaimed());
    }

    // ── Join listener — notify eligible players ───────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg.isEnabled()) return;
        Player player = event.getPlayer();
        if (hasClaimed(player.getUniqueId())) return;

        // Delay slightly so the player is fully in the world before we send a message
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            boolean registered = client.isPlayerRegistered(player.getUniqueId());
            if (!registered) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&6&l✦ &r&6You have an unclaimed website reward! "
                            + "Run &e/registerreward &6to claim it."));
                }
            });
        }, 60L); // 3-second delay
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    ItemStack buildItem(Map<?, ?> map) {
        ItemStack item = null;

        // ItemsAdder
        String iaId = (String) map.get("itemsadder");
        if (iaId != null && iaHook != null && iaHook.isEnabled()) {
            item = iaHook.buildItem(iaId);
        }

        // Nexo
        if (item == null) {
            String nexoId = (String) map.get("nexo");
            if (nexoId != null && nexoHook != null && nexoHook.isEnabled()) {
                item = nexoHook.buildItem(nexoId);
            }
        }

        // Vanilla
        if (item == null) {
            Material mat;
            try {
                Object matRaw = map.get("material");
                mat = Material.valueOf((matRaw != null ? String.valueOf(matRaw) : "STONE").toUpperCase());
            } catch (Exception e) {
                mat = Material.STONE;
            }
            int amount = map.containsKey("amount")
                    ? ((Number) map.get("amount")).intValue() : 1;
            item = new ItemStack(mat, amount);
        }

        applyMeta(item, map);
        return item;
    }

    private void applyMeta(ItemStack item, Map<?, ?> map) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Object nameRaw = map.get("name");
        if (nameRaw instanceof String name) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        Object loreRaw = map.get("lore");
        if (loreRaw instanceof List<?> loreList) {
            List<String> colored = new ArrayList<>();
            for (Object line : loreList) {
                colored.add(ChatColor.translateAlternateColorCodes('&', String.valueOf(line)));
            }
            meta.setLore(colored);
        }

        Object cmdRaw = map.get("custom-model-data");
        if (cmdRaw instanceof Number n && n.intValue() > 0) {
            try { meta.setCustomModelData(n.intValue()); } catch (Throwable ignored) {}
        }

        if (Boolean.TRUE.equals(map.get("enchanted"))) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }
}
