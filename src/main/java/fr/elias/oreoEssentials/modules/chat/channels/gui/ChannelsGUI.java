package fr.elias.oreoEssentials.modules.chat.channels.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ChannelsGUI implements InventoryProvider {

    private final OreoEssentials plugin;
    private final ChatChannelManager channelManager;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public ChannelsGUI(OreoEssentials plugin, ChatChannelManager channelManager) {
        this.plugin = plugin;
        this.channelManager = channelManager;
    }

    public static SmartInventory getInventory(OreoEssentials plugin, ChatChannelManager channelManager) {
        String title = plugin.getChatConfig().getCustomConfig().getString("chat.channels.gui.title", "§8Channels");
        int rows = plugin.getChatConfig().getCustomConfig().getInt("chat.channels.gui.rows", 4);

        Component titleComp = MM.deserialize(title);
        String legacyTitle = LEGACY.serialize(titleComp);

        return SmartInventory.builder()
                .id("channels-gui")
                .provider(new ChannelsGUI(plugin, channelManager))
                .manager(plugin.getInvManager())
                .size(rows, 9)
                .title(legacyTitle)
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        boolean fillGlass = plugin.getChatConfig().getCustomConfig().getBoolean("chat.channels.gui.fill_empty_with_glass", true);

        if (fillGlass) {
            contents.fillBorders(ClickableItem.empty(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)));
        }

        ChatChannel currentChannel = channelManager.getPlayerChannel(player);
        List<ChatChannel> channels = channelManager.getOrderedChannels();

        ChatChannel defaultChannel = resolveDefaultChannel(channels);
        boolean isInDefault = (currentChannel != null && defaultChannel != null
                && currentChannel.getId().equalsIgnoreCase(defaultChannel.getId()));

        ItemStack defaultItem = createDefaultChannelItem(defaultChannel, isInDefault);

        contents.set(1, 4, ClickableItem.of(defaultItem, e -> {
            if (!e.isLeftClick()) return;

            if (defaultChannel == null) {
                player.sendMessage("§cNo default channel is configured.");
                return;
            }

            if (!defaultChannel.isEnabled()) {
                player.sendMessage("§cThe default channel is currently disabled.");
                return;
            }

            if (!defaultChannel.canJoin(player)) {
                player.sendMessage("§cYou don't have permission to join the default channel.");
                return;
            }

            channelManager.setPlayerChannel(player, defaultChannel);
            Component msg = MM.deserialize("<green>You are now chatting in " + defaultChannel.getDisplayName() + "</green>");
            player.sendMessage(msg);
            player.closeInventory();
        }));

        int slot = 10;
        for (ChatChannel channel : channels) {
            int row = slot / 9;
            int col = slot % 9;

            if (col >= 8) {
                row++;
                col = 1;
                slot = row * 9 + col;
            }

            if (row >= contents.inventory().getRows() - 1) {
                break;
            }

            ItemStack item = createChannelItem(player, channel, currentChannel);

            contents.set(row, col, ClickableItem.of(item, e -> {
                if (!channel.isEnabled()) {
                    player.sendMessage("§cThis channel is currently disabled.");
                    return;
                }

                if (!channel.canJoin(player)) {
                    player.sendMessage("§cYou don't have permission to join this channel.");
                    return;
                }

                if (e.isLeftClick()) {
                    channelManager.setPlayerChannel(player, channel);
                    Component msg = MM.deserialize("<green>You are now chatting in " + channel.getDisplayName() + "</green>");
                    player.sendMessage(msg);
                    player.closeInventory();
                }
            }));

            slot++;
        }

        int lastRow = contents.inventory().getRows() - 1;
        contents.set(lastRow, 4, ClickableItem.of(
                createItem(Material.BARRIER, "§cClose", List.of()),
                e -> player.closeInventory()
        ));
    }


    private ChatChannel resolveDefaultChannel(List<ChatChannel> ordered) {

        try {
            ChatChannel def = channelManager.getDefaultChannel();
            if (def != null) return def;
        } catch (Throwable ignored) {
        }

        if (ordered == null || ordered.isEmpty()) return null;
        return ordered.get(0);
    }

    private ItemStack createDefaultChannelItem(ChatChannel defaultChannel, boolean isInDefault) {
        String name;
        List<String> lore = new ArrayList<>();

        if (defaultChannel == null) {
            name = "§cNo Default Channel";
            lore.add("§7Configure a default channel");
            lore.add("§7in your channels config.");
            return createItem(Material.RED_DYE, name, lore);
        }

        Component displayName = MM.deserialize(defaultChannel.getDisplayName());
        String legacyName = LEGACY.serialize(displayName);

        name = isInDefault ? ("§a§l✔ " + legacyName) : ("§e" + legacyName);

        lore.add("§7Set your active channel back");
        lore.add("§7to the default/global channel.");
        lore.add("");
        lore.add(isInDefault ? "§7Status: §a§lCurrent" : "§7Status: §eClick to switch");
        lore.add("");
        lore.add("§7Scope: §f" + getScopeDisplay(defaultChannel));

        if (defaultChannel.getScope() == ChatChannel.ChannelScope.RANGE) {
            lore.add("§7Range: §f" + defaultChannel.getRangeBlocks() + " blocks");
        }

        lore.add("");
        lore.add("§aLeft Click §7to use default");

        Material mat = isInDefault ? Material.LIME_DYE : Material.YELLOW_DYE;
        return createItem(mat, name, lore);
    }

    private ItemStack createChannelItem(Player player, ChatChannel channel, ChatChannel current) {
        boolean isCurrent = current != null && current.getId().equals(channel.getId());
        boolean canJoin = channel.canJoin(player);
        boolean canTalk = channel.canTalk(player);
        boolean enabled = channel.isEnabled();

        Material material;
        if (!enabled) {
            material = Material.GRAY_DYE;
        } else if (isCurrent) {
            material = Material.LIME_DYE;
        } else if (canJoin) {
            material = Material.GREEN_DYE;
        } else {
            material = Material.RED_DYE;
        }

        Component displayName = MM.deserialize(channel.getDisplayName());
        String legacyName = LEGACY.serialize(displayName);

        if (isCurrent) {
            legacyName = "§a§l✔ " + legacyName;
        }

        List<String> lore = new ArrayList<>();

        Component descComp = MM.deserialize(channel.getDescription());
        String legacyDesc = LEGACY.serialize(descComp);
        lore.add(legacyDesc);
        lore.add("");

        if (isCurrent) {
            lore.add("§7Status: §a§lCurrent Channel");
        } else if (!enabled) {
            lore.add("§7Status: §c§lDisabled");
        } else if (!canJoin) {
            lore.add("§7Status: §c§lLocked");
        } else {
            lore.add("§7Status: §aAvailable");
        }

        lore.add("");
        lore.add("§7Scope: §f" + getScopeDisplay(channel));

        if (channel.getScope() == ChatChannel.ChannelScope.RANGE) {
            lore.add("§7Range: §f" + channel.getRangeBlocks() + " blocks");
        }

        lore.add("");

        if (!canJoin) {
            lore.add("§c§l✘ No Permission");
        } else if (!canTalk) {
            lore.add("§e§l⚠ View Only");
        } else {
            lore.add("§aLeft Click §7to join");
        }

        return createItem(material, legacyName, lore);
    }

    private String getScopeDisplay(ChatChannel channel) {
        return switch (channel.getScope()) {
            case ALL -> "All Players";
            case WORLD -> "Same World";
            case RANGE -> "Local Area";
            case SERVER -> "This Server";
            case SHARD -> "This Shard";
        };
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}
