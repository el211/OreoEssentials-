// File: src/main/java/fr/elias/oreoEssentials/kits/ItemParser.java
package fr.elias.oreoEssentials.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemParser {
    private ItemParser() {}

    public static boolean isItemsAdderPresent() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    // --------- patterns (only once) ---------
    private static final Pattern TYPE   = Pattern.compile("type:([A-Z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile("amount:(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCH   = Pattern.compile("enchants:([A-Za-z0-9_:,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POTION_TYPE =
            Pattern.compile("potion_type:([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);

    public static String color(String s) {
        return s == null ? "" : s.replace('&','§').replace("\\n","\n");
    }

    /**
     * Accepts:
     *  - "ia:namespace:item_id" (ItemsAdder, if present & allowed)
     *  - "MATERIAL" (vanilla)
     *  - "type:MATERIAL;amount:1;enchants:SHARPNESS:2,UNBREAKING:1;potion_type:healing"
     */
    public static ItemStack parseItem(String def, boolean allowItemsAdder) {
        if (def == null || def.isBlank()) return null;

        // ItemsAdder short form
        if (allowItemsAdder && def.startsWith("ia:")) {
            try {
                // Requires ItemsAdder at runtime
                // dev.lone.itemsadder.api.CustomStack
                Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object custom = cs.getMethod("getInstance", String.class).invoke(null, def.substring(3));
                if (custom != null) {
                    Object is = cs.getMethod("getItemStack").invoke(custom);
                    return (ItemStack) is;
                }
            } catch (Throwable ignored) {
                // fallback below
            }
        }
        // Nexo short form
        if (def.toLowerCase(Locale.ROOT).startsWith("nexo:")) {
            try {
                // com.nexomc.nexo.api.NexoItems
                // NexoItems.itemFromId(String) -> ItemBuilder
                // ItemBuilder.build() or getItemStack() depending on API
                Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
                Object builder = nexoItems.getMethod("itemFromId", String.class).invoke(null, def.substring(5));
                if (builder != null) {
                    // Most builder APIs expose build()
                    try {
                        Object built = builder.getClass().getMethod("build").invoke(builder);
                        if (built instanceof ItemStack is) return is;
                    } catch (NoSuchMethodException ignored) {
                        // fallback names (some APIs)
                        Object built = builder.getClass().getMethod("getItemStack").invoke(builder);
                        if (built instanceof ItemStack is) return is;
                    }
                }
            } catch (Throwable ignored) {
                // fallback below
            }
        }

        // Our normal syntax: "type:...,amount:...,enchants:..."
        if (!def.contains(":") || def.toLowerCase(Locale.ROOT).startsWith("type:")) {
            return parseVanilla(def);
        }

        // Shortest: "DIAMOND_SWORD"
        Material mat = Material.matchMaterial(def.trim());
        if (mat != null) return new ItemStack(mat, 1);

        return null;
    }

    private static ItemStack parseVanilla(String def) {
        String d = def.trim();

        // If it's just a material name
        Material shortMat = Material.matchMaterial(d);
        if (shortMat != null) return new ItemStack(shortMat, 1);

        // Else parse "type:...,amount:...,enchants:..."
        Matcher mt = TYPE.matcher(d);
        if (!mt.find()) return null;
        Material mat = Material.matchMaterial(mt.group(1).toUpperCase(Locale.ROOT));
        if (mat == null) return null;

        int amount = 1;
        Matcher ma = AMOUNT.matcher(d);
        if (ma.find()) {
            try { amount = Math.max(1, Integer.parseInt(ma.group(1))); } catch (Exception ignored) {}
        }

        ItemStack is = new ItemStack(mat, amount);

        //  handle potion_type:...
        applyPotionMeta(is, d);

        // Enchantments
        Matcher me = ENCH.matcher(d);
        if (me.find()) {
            String blob = me.group(1);
            String[] parts = blob.split(",");
            for (String p : parts) {
                String[] kv = p.split(":");
                if (kv.length >= 1) {
                    String name = kv[0].trim().toUpperCase(Locale.ROOT);
                    int lvl = 1;
                    if (kv.length >= 2) {
                        try { lvl = Integer.parseInt(kv[1].trim()); } catch (Exception ignored) {}
                    }
                    try {
                        Enchantment ench = Enchantment.getByName(name);
                        if (ench != null) is.addUnsafeEnchantment(ench, lvl);
                    } catch (Throwable ignored) {}
                }
            }
        }

        return is;
    }

    // --------- potions support ---------
    private static void applyPotionMeta(ItemStack is, String def) {
        if (!(is.getItemMeta() instanceof PotionMeta meta)) return;

        Matcher mp = POTION_TYPE.matcher(def);
        if (!mp.find()) return;

        String raw = mp.group(1).trim();
        if (raw.isEmpty()) return;

        // Example inputs:
        //  healing, HEALING, strong_healing, LONG_SWIFTNESS, heal, speed, jump
        String key = raw.toUpperCase(Locale.ROOT); // strong_healing -> STRONG_HEALING

        // Friendly aliases -> real enum names
        switch (key) {
            case "HEAL" -> key = "HEALING";
            case "HARM" -> key = "HARMING";
            case "SPEED" -> key = "SWIFTNESS";
            case "JUMP", "JUMP_BOOST" -> key = "LEAPING";
            case "FIRERES", "FIRE_RES" -> key = "FIRE_RESISTANCE";
            case "WATERBREATHING" -> key = "WATER_BREATHING";
            case "NIGHTVISION" -> key = "NIGHT_VISION";
            case "REGEN" -> key = "REGENERATION";
            case "SLOWFALL" -> key = "SLOW_FALLING";
        }

        // strong_healing / long_swiftness already match STRONG_HEALING / LONG_SWIFTNESS here
        PotionType type;
        try {
            type = PotionType.valueOf(key);
        } catch (IllegalArgumentException ex) {
            // unknown potion type -> ignore
            return;
        }

        meta.setBasePotionType(type);
        is.setItemMeta(meta);
    }
}
