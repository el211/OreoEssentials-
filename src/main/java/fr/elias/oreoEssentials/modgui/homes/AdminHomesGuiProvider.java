package fr.elias.oreoEssentials.modgui.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
import fr.elias.oreoEssentials.modules.homes.home.HomeService.StoredHome;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.OtherHomeTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;

public class AdminHomesGuiProvider implements InventoryProvider {

    private final OreoEssentials plugin;
    private final HomeService homes;
    private final UUID targetUuid;
    private final String targetName;

    public AdminHomesGuiProvider(OreoEssentials plugin, HomeService homes, UUID targetUuid, String targetName) {
        this.plugin = plugin;
        this.homes = homes;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public static void open(Player admin, OreoEssentials plugin, UUID targetUuid, String targetName) {
        SmartInventory.builder()
                .id("oreo:modgui:homes:" + targetUuid)
                .provider(new AdminHomesGuiProvider(plugin, plugin.getHomeService(), targetUuid, targetName))
                .size(6, 9)
                .title(Lang.color("&8Homes: &b" + targetName))
                .manager(plugin.getInvManager())
                .build()
                .open(admin);
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        draw(p, contents);
    }

    @Override
    public void update(Player p, InventoryContents contents) {
    }

    private void draw(Player p, InventoryContents contents) {
        contents.fill(ClickableItem.empty(filler()));

        Map<String, StoredHome> data = safeListHomes();
        List<String> names = new ArrayList<>(data.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);

        contents.set(0, 4, ClickableItem.empty(headerItem(names.size())));
        contents.set(0, 8, ClickableItem.of(refreshItem(), e ->
                contents.inventory().open(p, contents.pagination().getPage())));

        Pagination pagination = contents.pagination();

        ClickableItem[] items = names.stream().map(name -> {
            StoredHome sh = data.get(name);
            String server = (sh != null && sh.getServer() != null) ? sh.getServer() : homes.localServer();
            return ClickableItem.of(homeItem(name, server, sh), e -> {
                switch (e.getClick()) {
                    case LEFT -> {
                        p.closeInventory();
                        teleportAdminToHome(p, name);
                    }
                    case RIGHT -> {
                        p.closeInventory();
                        openDeleteConfirm(p, name, pagination.getPage());
                    }
                    default -> {}
                }
            });
        }).toArray(ClickableItem[]::new);

        pagination.setItems(items);
        pagination.setItemsPerPage(28);

        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 0); it.blacklist(1, 8);
        it.blacklist(2, 0); it.blacklist(2, 8);
        it.blacklist(3, 0); it.blacklist(3, 8);
        it.blacklist(4, 0); it.blacklist(4, 8);
        pagination.addToIterator(it);

        if (!pagination.isFirst()) {
            contents.set(5, 0, ClickableItem.of(navItem(Material.ARROW, "&ePrevious page"), e ->
                    contents.inventory().open(p, pagination.previous().getPage())));
        }
        if (!pagination.isLast()) {
            contents.set(5, 8, ClickableItem.of(navItem(Material.ARROW, "&eNext page"), e ->
                    contents.inventory().open(p, pagination.next().getPage())));
        }
    }

    private void teleportAdminToHome(Player admin, String homeName) {
        final String localServer = homes.localServer();
        Async.run(() -> {
            String targetServer = homes.homeServer(targetUuid, homeName);
            if (targetServer == null) targetServer = localServer;
            final String ts = targetServer;

            if (ts.equalsIgnoreCase(localServer)) {
                Location loc = homes.getHome(targetUuid, homeName);
                OreScheduler.runForEntity(plugin, admin, () -> {
                    if (loc == null || loc.getWorld() == null) {
                        Lang.send(admin, "modgui.homes.not-found",
                                "<red>Home <yellow>%home%</yellow> not found or world not loaded.</red>",
                                Map.of("home", homeName));
                        return;
                    }
                    if (OreScheduler.isFolia()) {
                        admin.teleportAsync(loc).thenRun(() ->
                                Lang.send(admin, "modgui.homes.teleported",
                                        "<green>Teleported to <aqua>%name%</aqua>'s home <aqua>%home%</aqua>.</green>",
                                        Map.of("name", targetName, "home", homeName)));
                    } else {
                        admin.teleport(loc);
                        Lang.send(admin, "modgui.homes.teleported",
                                "<green>Teleported to <aqua>%name%</aqua>'s home <aqua>%home%</aqua>.</green>",
                                Map.of("name", targetName, "home", homeName));
                    }
                });
            } else {
                OreScheduler.runForEntity(plugin, admin, () -> {
                    PacketManager pm = plugin.getPacketManager();
                    if (pm != null && pm.isInitialized()) {
                        String requestId = UUID.randomUUID().toString();
                        OtherHomeTeleportRequestPacket pkt = new OtherHomeTeleportRequestPacket(
                                admin.getUniqueId(), targetUuid, homeName, ts, requestId);
                        pm.sendPacket(PacketChannel.individual(ts), pkt);
                        sendToServer(admin, ts);
                        Lang.send(admin, "modgui.homes.sending",
                                "<yellow>Sending you to <aqua>%server%</aqua>… teleport to <aqua>%name%</aqua>'s <aqua>%home%</aqua> on arrival.</yellow>",
                                Map.of("server", ts, "name", targetName, "home", homeName));
                    } else {
                        Lang.send(admin, "modgui.homes.cross-disabled",
                                "<red>Cross-server messaging is disabled.</red>");
                    }
                });
            }
        });
    }

    private void openDeleteConfirm(Player admin, String homeName, int page) {
        SmartInventory.builder()
                .id("oreo:modgui:homes:confirm:" + targetUuid)
                .provider(new ConfirmDeleteProvider(plugin, homes, targetUuid, targetName, homeName,
                        () -> open(admin, plugin, targetUuid, targetName)))
                .size(3, 9)
                .title(Lang.color("&cDelete &f" + targetName + "&c's &f" + homeName + "&c?"))
                .manager(plugin.getInvManager())
                .build()
                .open(admin);
    }

    private Map<String, StoredHome> safeListHomes() {
        try {
            Map<String, StoredHome> m = homes.listHomes(targetUuid);
            return (m == null) ? Collections.emptyMap() : m;
        } catch (Throwable t) {
            return Collections.emptyMap();
        }
    }

    private static void sendToServer(Player p, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", b.toByteArray());
        } catch (Exception ignored) {}
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); it.setItemMeta(meta); }
        return it;
    }

    private ItemStack headerItem(int count) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.color("&6" + targetName + "&e's homes &7(" + count + ")"));
            meta.setLore(List.of(
                    Lang.color("&7Left-click a home to &aTeleport&7."),
                    Lang.color("&7Right-click a home to &cDelete&7.")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack refreshItem() {
        ItemStack it = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.color("&eRefresh"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack homeItem(String name, String server, StoredHome sh) {
        ItemStack it = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.color("&b" + name));
            List<String> lore = new ArrayList<>();
            lore.add(Lang.color("&7Server: &f" + server));
            if (sh != null) {
                String world = sh.getWorld() != null ? sh.getWorld() : "(unloaded)";
                lore.add(Lang.color("&7World: &f" + world));
                lore.add(Lang.color("&7XYZ: &f" + fmt(sh.getX()) + " " + fmt(sh.getY()) + " " + fmt(sh.getZ())));
            }
            lore.add(" ");
            lore.add(Lang.color("&aLeft-Click: &fTeleport"));
            lore.add(Lang.color("&cRight-Click: &fDelete"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack navItem(Material type, String label) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(Lang.color(label)); it.setItemMeta(meta); }
        return it;
    }

    private String fmt(double d) {
        return String.valueOf(Math.round(d * 10.0) / 10.0);
    }

    // ── Confirm delete provider ────────────────────────────────────────────────

    private static final class ConfirmDeleteProvider implements InventoryProvider {
        private final OreoEssentials plugin;
        private final HomeService homes;
        private final UUID targetUuid;
        private final String targetName;
        private final String homeName;
        private final Runnable onDone;

        ConfirmDeleteProvider(OreoEssentials plugin, HomeService homes, UUID targetUuid,
                              String targetName, String homeName, Runnable onDone) {
            this.plugin = plugin;
            this.homes = homes;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.homeName = homeName;
            this.onDone = onDone;
        }

        @Override
        public void init(Player p, InventoryContents contents) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(Lang.color("&6Delete home"));
                infoMeta.setLore(List.of(
                        Lang.color("&7Are you sure you want to delete"),
                        Lang.color("&f" + targetName + "&7's home &e" + homeName + "&7?")
                ));
                info.setItemMeta(infoMeta);
            }
            contents.set(0, 4, ClickableItem.empty(info));

            ItemStack yes = new ItemStack(Material.GREEN_CONCRETE);
            ItemMeta yesMeta = yes.getItemMeta();
            if (yesMeta != null) { yesMeta.setDisplayName(Lang.color("&aYes, delete")); yes.setItemMeta(yesMeta); }
            contents.set(SlotPos.of(1, 3), ClickableItem.of(yes, e -> {
                boolean ok = homes.delHome(targetUuid, homeName.toLowerCase(Locale.ROOT));
                if (ok) {
                    Lang.send(p, "modgui.homes.deleted",
                            "<red>Deleted <yellow>%name%</yellow>'s home <yellow>%home%</yellow>.</red>",
                            Map.of("name", targetName, "home", homeName));
                } else {
                    Lang.send(p, "modgui.homes.delete-failed",
                            "<red>Failed to delete home <yellow>%home%</yellow>.</red>",
                            Map.of("home", homeName));
                }
                p.closeInventory();
                if (onDone != null) onDone.run();
            }));

            ItemStack no = new ItemStack(Material.RED_CONCRETE);
            ItemMeta noMeta = no.getItemMeta();
            if (noMeta != null) { noMeta.setDisplayName(Lang.color("&cNo, cancel")); no.setItemMeta(noMeta); }
            contents.set(SlotPos.of(1, 5), ClickableItem.of(no, e -> {
                p.closeInventory();
                if (onDone != null) onDone.run();
            }));
        }

        @Override
        public void update(Player p, InventoryContents contents) {}
    }
}
