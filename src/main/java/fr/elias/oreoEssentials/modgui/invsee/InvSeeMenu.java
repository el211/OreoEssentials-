package fr.elias.oreoEssentials.modgui.invsee;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.invsee.InvseeService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.services.InventoryService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.UUID;

public class InvSeeMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final UUID targetId;

    public InvSeeMenu(OreoEssentials plugin, UUID targetId) {
        this.plugin = plugin;
        this.targetId = targetId;
    }

    public static void open(OreoEssentials plugin, Player viewer, UUID targetId) {
        // ✅ BEDROCK PROTECTION - CHECK BEFORE OPENING
        if (isBedrockPlayer(targetId)) {
            viewer.sendMessage("§c§lCannot view inventory of Bedrock players!");
            viewer.sendMessage("§7Bedrock items have different NBT data that causes duplication issues.");
            viewer.sendMessage("§7This is a protection to prevent item corruption.");
            return;
        }

        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        String targetName =
                (op.getName() != null ? op.getName() : targetId.toString().substring(0, 8));

        plugin.getLogger().info("[INVSEE-DEBUG] open(): viewer=" + viewer.getName()
                + " targetId=" + targetId + " targetName=" + targetName);

        SmartInventory.builder()
                .manager(plugin.getInvManager())
                .provider(new InvSeeMenu(plugin, targetId))
                .title("§8Inventory of " + targetName)
                .size(6, 9)
                .closeable(true)
                .listener(new InventoryListener<>(InventoryCloseEvent.class, ev -> {
                    Player staff = (Player) ev.getPlayer();
                    Inventory top = ev.getInventory();
                    plugin.getLogger().info("[INVSEE-DEBUG] InventoryCloseEvent for viewer="
                            + staff.getName() + " targetId=" + targetId
                            + " topSize=" + top.getSize());
                    syncAndApply(plugin, staff, targetId, top);
                }))
                .build()
                .open(viewer);
    }

    @Override
    public void init(Player viewer, InventoryContents contents) {
        plugin.getLogger().info("[INVSEE-DEBUG] init(): viewer=" + viewer.getName()
                + " targetId=" + targetId + " on server=" + plugin.getServerNameSafe());

        InvseeService invsee = plugin.getInvseeService();
        if (invsee == null) {
            plugin.getLogger().warning("[INVSEE-DEBUG] init(): InvseeService is null, aborting.");
            viewer.closeInventory();
            viewer.sendMessage("§cCross-server invsee service is not available.");
            return;
        }

        // Ask InvseeService for the *best* snapshot it can get (online local or persisted).
        InventoryService.Snapshot snap = invsee.requestSnapshot(targetId);
        if (snap == null) {
            plugin.getLogger().warning("[INVSEE-DEBUG] init(): requestSnapshot returned NULL for targetId=" + targetId);
            viewer.closeInventory();
            viewer.sendMessage("§cCould not fetch inventory. " +
                    "Player may be offline or has no stored inventory.");
            return;
        }

        if (snap.contents == null) snap.contents = new ItemStack[41];
        if (snap.armor == null)    snap.armor    = new ItemStack[4];

        plugin.getLogger().info("[INVSEE-DEBUG] init(): got snapshot for targetId=" + targetId
                + " contentsLen=" + snap.contents.length
                + " armorLen=" + snap.armor.length
                + " offhand=" + (snap.offhand == null ? "null" : snap.offhand.getType().name()));

        for (int i = 0; i < 41; i++) {
            int row = i / 9;
            int col = i % 9;
            ItemStack it = snap.contents[i];

            contents.set(row, col, ClickableItem.of(
                    (it == null ? new ItemStack(Material.AIR) : it),
                    e -> {
                    }
            ));
        }

        for (int i = 0; i < 4; i++) {
            int raw = 45 + i;
            int row = raw / 9; // 5
            int col = raw % 9;

            ItemStack it = (snap.armor.length > i ? snap.armor[i] : null);
            contents.set(row, col, ClickableItem.of(
                    (it == null ? new ItemStack(Material.AIR) : it),
                    e -> {
                    }
            ));
        }

        {
            int raw = 49;
            int row = raw / 9; // 5
            int col = raw % 9;

            ItemStack off = snap.offhand;
            contents.set(row, col, ClickableItem.of(
                    (off == null ? new ItemStack(Material.AIR) : off),
                    e -> {
                    }
            ));
        }

        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        String targetName =
                (op.getName() != null ? op.getName() : targetId.toString().substring(0, 8));

        contents.set(5, 8, ClickableItem.empty(
                new ItemBuilder(Material.BOOK)
                        .name("§bEditing inventory of §e" + targetName)
                        .lore(
                                "§7Changes will be pushed to",
                                "§7the live player / stored snapshot."
                        )
                        .build()
        ));

        plugin.getLogger().info("[INVSEE-DEBUG] init(): GUI prepared for viewer=" + viewer.getName()
                + " targetId=" + targetId);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // No periodic animation required.
    }

    public static void syncAndApply(OreoEssentials plugin,
                                    Player viewer,
                                    UUID targetId,
                                    Inventory inv) {
        plugin.getLogger().info("[INVSEE-DEBUG] syncAndApply(): viewer=" + viewer.getName()
                + " targetId=" + targetId + " invSize=" + inv.getSize());

        InvseeService invsee = plugin.getInvseeService();
        if (invsee == null) {
            plugin.getLogger().warning("[INVSEE-DEBUG] syncAndApply(): InvseeService is null, aborting.");
            viewer.sendMessage("§cCannot apply inventory changes: invsee service unavailable.");
            return;
        }

        InventoryService.Snapshot snap = new InventoryService.Snapshot();
        snap.contents = new ItemStack[41];
        snap.armor    = new ItemStack[4];

        for (int i = 0; i < 41 && i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            snap.contents[i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }

        for (int i = 0; i < 4; i++) {
            int raw = 45 + i;
            if (raw >= inv.getSize()) break;
            ItemStack it = inv.getItem(raw);
            snap.armor[i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }

        if (49 < inv.getSize()) {
            ItemStack it = inv.getItem(49);
            snap.offhand = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }

        boolean allEmpty = true;
        int nonEmptyContents = 0;
        int nonEmptyArmor = 0;
        boolean offhandNonEmpty = false;

        for (ItemStack it : snap.contents) {
            if (it != null && it.getType() != Material.AIR) {
                allEmpty = false;
                nonEmptyContents++;
            }
        }
        if (allEmpty) {
            for (ItemStack it : snap.armor) {
                if (it != null && it.getType() != Material.AIR) {
                    allEmpty = false;
                    nonEmptyArmor++;
                }
            }
        } else {
            for (ItemStack it : snap.armor) {
                if (it != null && it.getType() != Material.AIR) {
                    nonEmptyArmor++;
                }
            }
        }
        if (snap.offhand != null && snap.offhand.getType() != Material.AIR) {
            allEmpty = false;
            offhandNonEmpty = true;
        }

        plugin.getLogger().info("[INVSEE-DEBUG] syncAndApply(): nonEmptyContents=" + nonEmptyContents
                + " nonEmptyArmor=" + nonEmptyArmor
                + " offhandNonEmpty=" + offhandNonEmpty);

        if (allEmpty) {
            plugin.getLogger().warning("[INVSEE-DEBUG] syncAndApply(): snapshot fully empty, NOT applying. targetId=" + targetId);
            viewer.sendMessage("§cNot applying an empty inventory snapshot " +
                    "(inventory probably failed to load).");
            return;
        }

        boolean ok = invsee.applySnapshotFromGui(viewer.getUniqueId(), targetId, snap);
        plugin.getLogger().info("[INVSEE-DEBUG] syncAndApply(): applySnapshotFromGui returned " + ok
                + " for targetId=" + targetId);

        if (!ok) {
            viewer.sendMessage("§cFailed to apply inventory remotely. " +
                    "Player may be offline or on an unreachable server.");
        } else {
            viewer.sendMessage("§aInventory updated across the network.");
        }

        logChange(plugin, viewer, targetId);
    }

    /**
     * Check if player is from Bedrock Edition using Floodgate API
     *
     * @param uuid The player's UUID to check
     * @return true if player is from Bedrock, false otherwise
     */
    private static boolean isBedrockPlayer(UUID uuid) {
        try {
            // Load Floodgate API class
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");

            // Get FloodgateApi instance
            Object instance = floodgateApi.getMethod("getInstance").invoke(null);

            // Call isFloodgatePlayer(UUID)
            Boolean result = (Boolean) floodgateApi
                    .getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(instance, uuid);

            return result != null && result;

        } catch (ClassNotFoundException e) {
            // Floodgate not installed - all players are Java Edition
            return false;

        } catch (NoSuchMethodException e) {
            OreoEssentials.get().getLogger().warning(
                    "[BEDROCK-CHECK] Floodgate API method not found. Using older Floodgate version? " + e.getMessage()
            );
            return false;

        } catch (Exception e) {
            OreoEssentials.get().getLogger().warning(
                    "[BEDROCK-CHECK] Error checking Floodgate API for UUID " + uuid + ": " + e.getMessage()
            );
            return false;
        }
    }

    private static void logChange(OreoEssentials plugin, Player staff, UUID targetId) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
            String targetName = (op.getName() != null ? op.getName() : targetId.toString());

            java.io.File file = new java.io.File(plugin.getDataFolder(), "playeractions.log");
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.println(LocalDateTime.now() + " [INVSEE] "
                        + staff.getName() + " edited inventory of " + targetName);
            }
        } catch (Exception ignored) {
        }
    }
}