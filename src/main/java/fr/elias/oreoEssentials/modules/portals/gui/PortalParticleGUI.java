package fr.elias.oreoEssentials.modules.portals.gui;

import fr.elias.oreoEssentials.modules.portals.PortalParticleConfig;
import fr.elias.oreoEssentials.modules.portals.PortalsManager;
import fr.elias.oreoEssentials.modules.portals.PortalsManager.Portal;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Particle editor GUI for a single portal.
 *
 * Lets admins configure:
 *  - Teleport particle: type (from a preset list) + count
 *  - Ambient particle:  enabled toggle + type + count
 */
public final class PortalParticleGUI implements InventoryProvider {

    // Common particles to offer as presets
    private static final String[] PRESET_TYPES = {
            "PORTAL", "FLAME", "SOUL_FIRE_FLAME", "END_ROD", "WITCH",
            "HAPPY_VILLAGER", "SMOKE_NORMAL", "SMOKE_LARGE", "SPELL_MOB",
            "EXPLOSION_NORMAL", "DRIP_LAVA", "ENCHANTMENT_TABLE", "TOTEM"
    };
    private static final int[] PRESET_COUNTS = {5, 10, 20, 40, 80};

    private final PortalsManager manager;
    private final String portalName;

    private PortalParticleGUI(PortalsManager manager, String portalName) {
        this.manager    = manager;
        this.portalName = portalName;
    }

    public static SmartInventory getInventory(PortalsManager manager, String portalName) {
        return SmartInventory.builder()
                .id("oe_portal_particles_" + portalName)
                .provider(new PortalParticleGUI(manager, portalName))
                .manager(manager.getPlugin().getInvManager())
                .size(5, 9)
                .title(ChatColor.DARK_PURPLE + "Particles: " + portalName)
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Portal portal = manager.get(portalName);
        if (portal == null) { player.closeInventory(); return; }

        PortalParticleConfig pc = portal.particles;
        String curTeleportType = pc.hasTeleportOverride() ? pc.teleportType : "(global default)";
        int curTeleportCount   = pc.teleportCount > 0 ? pc.teleportCount : -1;

        // ── Row 0: header ──────────────────────────────────────────────────
        ItemStack border = glass(Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) contents.set(0, i, ClickableItem.empty(border));
        for (int i = 0; i < 9; i++) contents.set(4, i, ClickableItem.empty(border));

        // ── Teleport particle section (rows 1-2) ───────────────────────────
        contents.set(0, 1, ClickableItem.empty(named(Material.BLAZE_POWDER, "&d&lTeleport Particles")));

        // Particle type presets
        for (int i = 0; i < PRESET_TYPES.length && i < 9; i++) {
            final String type = PRESET_TYPES[i];
            boolean active = type.equals(pc.teleportType);
            contents.set(1, i, ClickableItem.of(
                    named(active ? Material.MAGENTA_DYE : Material.GRAY_DYE,
                            (active ? "&d&l" : "&7") + type,
                            List.of("&7Click to set as teleport particle",
                                    active ? "&a✔ Currently selected" : "")),
                    e -> {
                        manager.updateParticleTeleportType(portalName, type);
                        refresh(player);
                    }));
        }

        // Count options
        for (int i = 0; i < PRESET_COUNTS.length; i++) {
            final int count = PRESET_COUNTS[i];
            boolean active = (pc.teleportCount == count);
            contents.set(2, i + 2, ClickableItem.of(
                    named(active ? Material.LIME_DYE : Material.GRAY_DYE,
                            (active ? "&a&l" : "&7") + "×" + count,
                            List.of("&7Set teleport particle count to &e" + count)),
                    e -> {
                        manager.updateParticleTeleportCount(portalName, count);
                        refresh(player);
                    }));
        }

        // Reset teleport to global default
        contents.set(2, 0, ClickableItem.of(
                named(Material.RED_DYE, "&c&lReset to Default",
                        List.of("&7Revert to the global default particle.")),
                e -> {
                    manager.updateParticleTeleportType(portalName, "");
                    manager.updateParticleTeleportCount(portalName, -1);
                    refresh(player);
                }));

        // ── Ambient particle section (rows 3) ──────────────────────────────
        contents.set(0, 5, ClickableItem.empty(named(Material.SOUL_LANTERN, "&5&lAmbient Particles")));

        // Toggle ambient on/off
        boolean ambOn = pc.ambientEnabled;
        contents.set(3, 0, ClickableItem.of(
                named(ambOn ? Material.LIME_DYE : Material.GRAY_DYE,
                        "&5Ambient: " + (ambOn ? "&aENABLED" : "&cDISABLED"),
                        List.of("&7Toggle continuous ambient", "&7particles inside the portal.")),
                e -> {
                    manager.toggleAmbientParticles(portalName);
                    refresh(player);
                }));

        // Ambient type presets
        for (int i = 0; i < Math.min(PRESET_TYPES.length, 7); i++) {
            final String type = PRESET_TYPES[i];
            boolean active = type.equals(pc.ambientType);
            contents.set(3, i + 1, ClickableItem.of(
                    named(active ? Material.PURPLE_DYE : Material.GRAY_DYE,
                            (active ? "&5&l" : "&7") + type,
                            List.of("&7Set ambient particle type")),
                    e -> {
                        manager.updateParticleAmbientType(portalName, type);
                        refresh(player);
                    }));
        }

        // ── Back button ────────────────────────────────────────────────────
        contents.set(4, 0, ClickableItem.of(
                named(Material.BARRIER, "&c&lBack"),
                e -> PortalEditGUI.getInventory(manager, portalName).open(player)));

        // ── Current status display ─────────────────────────────────────────
        contents.set(4, 4, ClickableItem.empty(statusItem(pc, curTeleportType, curTeleportCount)));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    private void refresh(Player player) {
        getInventory(manager, portalName).open(player);
    }

    private ItemStack statusItem(PortalParticleConfig pc, String tType, int tCount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(c("&f&lCurrent Settings"));
        List<String> lore = new ArrayList<>();
        lore.add(c("&7Teleport type: &d" + tType));
        lore.add(c("&7Teleport count: &d" + (tCount < 0 ? "(default)" : tCount)));
        lore.add(c("&7Ambient: " + (pc.ambientEnabled ? "&aON" : "&cOFF")));
        if (pc.ambientEnabled) {
            lore.add(c("&7Ambient type: &5" + (pc.ambientType.isEmpty() ? "(global)" : pc.ambientType)));
            lore.add(c("&7Ambient count: &5" + pc.ambientCount));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(name));
            meta.setLore(lore.stream().map(PortalParticleGUI::c).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack named(Material mat, String name) {
        return named(mat, name, List.of());
    }

    private static ItemStack glass(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
