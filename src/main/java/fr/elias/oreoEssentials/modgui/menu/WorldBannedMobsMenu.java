package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WorldBannedMobsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final World world;

    private static final List<EntityType> CATALOG = Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.SLIME, EntityType.WITCH,
            EntityType.PHANTOM, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.GHAST, EntityType.ENDERMITE,
            EntityType.SILVERFISH, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN,
            EntityType.SHULKER, EntityType.DROWNED, EntityType.HUSK, EntityType.STRAY,
            EntityType.VEX, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.PILLAGER,
            EntityType.RAVAGER, EntityType.WARDEN, EntityType.BREEZE,
            EntityType.WITHER, EntityType.ENDER_DRAGON,
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.WOLF, EntityType.CAT, EntityType.OCELOT, EntityType.HORSE,
            EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.TRADER_LLAMA,
            EntityType.PARROT, EntityType.BAT, EntityType.RABBIT, EntityType.POLAR_BEAR,
            EntityType.PANDA, EntityType.FOX, EntityType.BEE, EntityType.GOAT,
            EntityType.AXOLOTL, EntityType.GLOW_SQUID, EntityType.SQUID,
            EntityType.DOLPHIN, EntityType.TURTLE, EntityType.COD, EntityType.SALMON,
            EntityType.PUFFERFISH, EntityType.TROPICAL_FISH, EntityType.MOOSHROOM,
            EntityType.STRIDER, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE,
            EntityType.CAMEL, EntityType.SNIFFER, EntityType.FROG, EntityType.TADPOLE,
            EntityType.ALLAY, EntityType.ARMADILLO,
            EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.IRON_GOLEM,
            EntityType.SNOW_GOLEM
    );

    private static final Map<EntityType, Material> EGG = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE, Material.ZOMBIE_SPAWN_EGG),
            Map.entry(EntityType.SKELETON, Material.SKELETON_SPAWN_EGG),
            Map.entry(EntityType.CREEPER, Material.CREEPER_SPAWN_EGG),
            Map.entry(EntityType.SPIDER, Material.SPIDER_SPAWN_EGG),
            Map.entry(EntityType.CAVE_SPIDER, Material.CAVE_SPIDER_SPAWN_EGG),
            Map.entry(EntityType.ENDERMAN, Material.ENDERMAN_SPAWN_EGG),
            Map.entry(EntityType.SLIME, Material.SLIME_SPAWN_EGG),
            Map.entry(EntityType.WITCH, Material.WITCH_SPAWN_EGG),
            Map.entry(EntityType.PHANTOM, Material.PHANTOM_SPAWN_EGG),
            Map.entry(EntityType.BLAZE, Material.BLAZE_SPAWN_EGG),
            Map.entry(EntityType.MAGMA_CUBE, Material.MAGMA_CUBE_SPAWN_EGG),
            Map.entry(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SPAWN_EGG),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, Material.ZOMBIFIED_PIGLIN_SPAWN_EGG),
            Map.entry(EntityType.PIGLIN, Material.PIGLIN_SPAWN_EGG),
            Map.entry(EntityType.PIGLIN_BRUTE, Material.PIGLIN_BRUTE_SPAWN_EGG),
            Map.entry(EntityType.HOGLIN, Material.HOGLIN_SPAWN_EGG),
            Map.entry(EntityType.ZOGLIN, Material.ZOGLIN_SPAWN_EGG),
            Map.entry(EntityType.GHAST, Material.GHAST_SPAWN_EGG),
            Map.entry(EntityType.ENDERMITE, Material.ENDERMITE_SPAWN_EGG),
            Map.entry(EntityType.SILVERFISH, Material.SILVERFISH_SPAWN_EGG),
            Map.entry(EntityType.GUARDIAN, Material.GUARDIAN_SPAWN_EGG),
            Map.entry(EntityType.ELDER_GUARDIAN, Material.ELDER_GUARDIAN_SPAWN_EGG),
            Map.entry(EntityType.SHULKER, Material.SHULKER_SPAWN_EGG),
            Map.entry(EntityType.DROWNED, Material.DROWNED_SPAWN_EGG),
            Map.entry(EntityType.HUSK, Material.HUSK_SPAWN_EGG),
            Map.entry(EntityType.STRAY, Material.STRAY_SPAWN_EGG),
            Map.entry(EntityType.VEX, Material.VEX_SPAWN_EGG),
            Map.entry(EntityType.VINDICATOR, Material.VINDICATOR_SPAWN_EGG),
            Map.entry(EntityType.EVOKER, Material.EVOKER_SPAWN_EGG),
            Map.entry(EntityType.PILLAGER, Material.PILLAGER_SPAWN_EGG),
            Map.entry(EntityType.RAVAGER, Material.RAVAGER_SPAWN_EGG),
            Map.entry(EntityType.WARDEN, Material.REINFORCED_DEEPSLATE),
            Map.entry(EntityType.BREEZE, Material.TRIAL_SPAWNER),
            Map.entry(EntityType.WITHER, Material.WITHER_SKELETON_SKULL),
            Map.entry(EntityType.ENDER_DRAGON, Material.DRAGON_HEAD),
            Map.entry(EntityType.PIG, Material.PIG_SPAWN_EGG),
            Map.entry(EntityType.COW, Material.COW_SPAWN_EGG),
            Map.entry(EntityType.SHEEP, Material.SHEEP_SPAWN_EGG),
            Map.entry(EntityType.CHICKEN, Material.CHICKEN_SPAWN_EGG),
            Map.entry(EntityType.WOLF, Material.WOLF_SPAWN_EGG),
            Map.entry(EntityType.CAT, Material.CAT_SPAWN_EGG),
            Map.entry(EntityType.OCELOT, Material.OCELOT_SPAWN_EGG),
            Map.entry(EntityType.HORSE, Material.HORSE_SPAWN_EGG),
            Map.entry(EntityType.DONKEY, Material.DONKEY_SPAWN_EGG),
            Map.entry(EntityType.MULE, Material.MULE_SPAWN_EGG),
            Map.entry(EntityType.LLAMA, Material.LLAMA_SPAWN_EGG),
            Map.entry(EntityType.TRADER_LLAMA, Material.TRADER_LLAMA_SPAWN_EGG),
            Map.entry(EntityType.PARROT, Material.PARROT_SPAWN_EGG),
            Map.entry(EntityType.BAT, Material.BAT_SPAWN_EGG),
            Map.entry(EntityType.RABBIT, Material.RABBIT_SPAWN_EGG),
            Map.entry(EntityType.POLAR_BEAR, Material.POLAR_BEAR_SPAWN_EGG),
            Map.entry(EntityType.PANDA, Material.PANDA_SPAWN_EGG),
            Map.entry(EntityType.FOX, Material.FOX_SPAWN_EGG),
            Map.entry(EntityType.BEE, Material.BEE_SPAWN_EGG),
            Map.entry(EntityType.GOAT, Material.GOAT_SPAWN_EGG),
            Map.entry(EntityType.AXOLOTL, Material.AXOLOTL_SPAWN_EGG),
            Map.entry(EntityType.GLOW_SQUID, Material.GLOW_SQUID_SPAWN_EGG),
            Map.entry(EntityType.SQUID, Material.SQUID_SPAWN_EGG),
            Map.entry(EntityType.DOLPHIN, Material.DOLPHIN_SPAWN_EGG),
            Map.entry(EntityType.TURTLE, Material.TURTLE_SPAWN_EGG),
            Map.entry(EntityType.COD, Material.COD_SPAWN_EGG),
            Map.entry(EntityType.SALMON, Material.SALMON_SPAWN_EGG),
            Map.entry(EntityType.PUFFERFISH, Material.PUFFERFISH_SPAWN_EGG),
            Map.entry(EntityType.TROPICAL_FISH, Material.TROPICAL_FISH_SPAWN_EGG),
            Map.entry(EntityType.MOOSHROOM, Material.MOOSHROOM_SPAWN_EGG),
            Map.entry(EntityType.STRIDER, Material.STRIDER_SPAWN_EGG),
            Map.entry(EntityType.SKELETON_HORSE, Material.SKELETON_HORSE_SPAWN_EGG),
            Map.entry(EntityType.ZOMBIE_HORSE, Material.ZOMBIE_HORSE_SPAWN_EGG),
            Map.entry(EntityType.CAMEL, Material.CAMEL_SPAWN_EGG),
            Map.entry(EntityType.SNIFFER, Material.SNIFFER_SPAWN_EGG),
            Map.entry(EntityType.FROG, Material.FROG_SPAWN_EGG),
            Map.entry(EntityType.TADPOLE, Material.TADPOLE_SPAWN_EGG),
            Map.entry(EntityType.ALLAY, Material.ALLAY_SPAWN_EGG),
            Map.entry(EntityType.ARMADILLO, Material.ARMADILLO_SPAWN_EGG),
            Map.entry(EntityType.VILLAGER, Material.VILLAGER_SPAWN_EGG),
            Map.entry(EntityType.WANDERING_TRADER, Material.WANDERING_TRADER_SPAWN_EGG),
            Map.entry(EntityType.IRON_GOLEM, Material.IRON_BLOCK),
            Map.entry(EntityType.SNOW_GOLEM, Material.SNOW_BLOCK)
    );

    public WorldBannedMobsMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin = plugin;
        this.svc = svc;
        this.world = world;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        int row = 1, col = 1;

        for (EntityType type : CATALOG) {
            boolean banned = svc.cfg().isMobBanned(world, type.name());
            ItemStack item = createMobItem(type, banned);

            c.set(row, col, ClickableItem.of(item, e -> handleMobToggle(p, c, type)));

            if (++col >= 8) {
                col = 1;
                row++;
                if (row >= 5) break;
            }
        }
    }

    private ItemStack createMobItem(EntityType type, boolean banned) {
        Material icon = EGG.getOrDefault(type, Material.BARRIER);
        String displayName = formatMobName(type);

        ItemStack item = new ItemBuilder(icon)
                .name((banned ? "&c" : "&a") + displayName)
                .lore(
                        "&7World: &f" + world.getName(),
                        "&7Status: " + (banned ? "&cBANNED" : "&aALLOWED"),
                        "&8Click to toggle"
                )
                .build();

        if (banned) {
            applyGlowEffect(item);
        }

        return item;
    }

    private void handleMobToggle(Player p, InventoryContents c, EntityType type) {
        svc.cfg().toggleMobBan(world, type.name());
        boolean nowBanned = svc.cfg().isMobBanned(world, type.name());
        String displayName = formatMobName(type);

        if (nowBanned) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.7f);
            Lang.send(p, "modgui.banned-mobs.banned",
                    "<red>%mob%</red> <gray>is now</gray> <red>BANNED</red> <gray>in</gray> <white>%world%</white>",
                    Map.of("mob", displayName, "world", world.getName()));
        } else {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            Lang.send(p, "modgui.banned-mobs.allowed",
                    "<green>%mob%</green> <gray>is now</gray> <green>ALLOWED</green> <gray>in</gray> <white>%world%</white>",
                    Map.of("mob", displayName, "world", world.getName()));
        }

        init(p, c);
    }

    private String formatMobName(EntityType type) {
        return capitalize(type.name().replace('_', ' '));
    }

    private void applyGlowEffect(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        try {
            ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class)
                    .invoke(meta, Boolean.TRUE);
            stack.setItemMeta(meta);
            return;
        } catch (Throwable ignored) {
        }

        stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        meta = stack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        String[] parts = s.toLowerCase().split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
        }
        return out.toString().trim();
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}