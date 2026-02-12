package fr.elias.oreoEssentials.modules.playervaults;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;


public final class PlayerVaultsConfig {

    private static final String MINECRAFT_NS = "minecraft:";

    private final boolean enabled;
    private final String storage;
    private final String collection;

    private final int maxVaults;

    private final int slotsCap;
    private final int defaultSlots;

    private final String denyMessage;

    private final String openSoundKey;
    private final String denySoundKey;

    private final String menuTitle;
    private final String menuItemUnlockedName;
    private final String menuItemLockedName;
    private final String menuItemUnlockedLore;
    private final String menuItemLockedLore;

    private final String vaultTitle;

    private final Map<String, Integer> accessGlobal;

    private final Map<Integer, Map<String, Integer>> slotsPerVault;

    public PlayerVaultsConfig(OreoEssentials plugin) {
        ConfigurationSection cs = plugin.getConfig().getConfigurationSection("playervaults");
        if (cs == null) cs = plugin.getConfig().createSection("playervaults");

        enabled = plugin.getSettings().playerVaultsEnabled();
        storage    = cs.getString("storage", "auto");
        collection = cs.getString("collection", "oreo_playervaults");

        maxVaults   = clamp(cs.getInt("max-vaults", 36), 1, 54);
        slotsCap     = clamp(cs.getInt("slots-cap", 54), 1, 54);
        defaultSlots = clamp(cs.getInt("default-slots", 9), 1, slotsCap);

        denyMessage = cs.getString("deny-message", "&cYou don't have permission to access vault &f#%id%&c.");

        ConfigurationSection snd = cs.getConfigurationSection("sounds");
        String rawOpen = (snd != null ? snd.getString("open", "minecraft:ui.button.click") : "minecraft:ui.button.click");
        String rawDeny = (snd != null ? snd.getString("deny", "minecraft:entity.villager.no") : "minecraft:entity.villager.no");
        openSoundKey = normalizeSoundKey(rawOpen);
        denySoundKey = normalizeSoundKey(rawDeny);

        ConfigurationSection menu = cs.getConfigurationSection("menu");
        menuTitle            = opt(menu, "title", "&8Player Vaults");
        menuItemUnlockedName = opt(menu, "item-unlocked-name", "&aVault &f#<id>");
        menuItemLockedName   = opt(menu, "item-locked-name", "&cVault &f#<id> &7(locked)");
        menuItemUnlockedLore = opt(menu, "item-unlocked-lore", "&7Click to open");
        menuItemLockedLore   = opt(menu, "item-locked-lore", "&7You don't have access");

        vaultTitle = cs.getString("vault-title", "&8Vault &f#<id> &7(<rows>x9)");

        Map<String, Integer> access = new HashMap<>();
        Map<Integer, Map<String, Integer>> perVault = new HashMap<>();

        ConfigurationSection vpr = cs.getConfigurationSection("vaults-per-rank");
        if (vpr != null) {
            ConfigurationSection global = vpr.getConfigurationSection("global");
            if (global != null) {
                for (String k : global.getKeys(false)) {
                    int val = global.getInt(k, -1);
                    if (val >= 0) access.put(k.toLowerCase(Locale.ROOT), clamp(val, 0, maxVaults));
                }
            }
            ConfigurationSection pv = vpr.getConfigurationSection("per-vault");
            if (pv != null) {
                for (String vk : pv.getKeys(false)) {
                    int id;
                    try { id = Integer.parseInt(vk); } catch (NumberFormatException e) { continue; }
                    ConfigurationSection rSec = pv.getConfigurationSection(vk);
                    if (rSec == null) continue;
                    Map<String, Integer> ranks = new HashMap<>();
                    for (String rk : rSec.getKeys(false)) {
                        int slots = rSec.getInt(rk, -1);
                        if (slots > 0) ranks.put(rk.toLowerCase(Locale.ROOT), clamp(slots, 1, slotsCap));
                    }
                    if (!ranks.isEmpty()) perVault.put(id, Collections.unmodifiableMap(ranks));
                }
            }
        }

        accessGlobal  = Collections.unmodifiableMap(access);
        slotsPerVault = Collections.unmodifiableMap(perVault);
    }


    public boolean enabled() { return enabled; }
    public String storage() { return storage; }
    public String collection() { return collection; }

    public int maxVaults() { return maxVaults; }

    public int slotsCap() { return slotsCap; }
    public int slotCap() { return slotsCap; }

    public int defaultSlots() { return defaultSlots; }

    public String denyMessage() { return denyMessage; }

    public String openSoundKey() { return openSoundKey; }
    public String denySoundKey() { return denySoundKey; }



    @Deprecated(forRemoval = true)
    public Sound openSound() {
        return legacyGuessEnum(openSoundKey, Sound.UI_BUTTON_CLICK);
    }

    @Deprecated(forRemoval = true)
    public Sound denySound() {
        return legacyGuessEnum(denySoundKey, Sound.ENTITY_VILLAGER_NO);
    }

    public String menuTitle() { return menuTitle; }
    public String menuItemUnlockedName() { return menuItemUnlockedName; }
    public String menuItemLockedName() { return menuItemLockedName; }
    public String menuItemUnlockedLore() { return menuItemUnlockedLore; }
    public String menuItemLockedLore() { return menuItemLockedLore; }

    public String vaultTitle() { return vaultTitle; }

    public int unlockedCountFor(String rankOrNull) {
        if (rankOrNull != null) {
            Integer v = accessGlobal.get(rankOrNull.toLowerCase(Locale.ROOT));
            if (v != null) return clamp(v, 0, maxVaults);
        }
        return clamp(accessGlobal.getOrDefault("default", 0), 0, maxVaults);
    }

    public int slotsFromConfig(int vaultId, String rankOrNull) {
        Map<String, Integer> ranks = slotsPerVault.get(vaultId);
        if (ranks == null) return -1;
        if (rankOrNull != null) {
            Integer v = ranks.get(rankOrNull.toLowerCase(Locale.ROOT));
            if (v != null) return clamp(v, 1, slotsCap);
        }
        Integer def = ranks.get("default");
        return def != null ? clamp(def, 1, slotsCap) : -1;
    }


    private static String opt(ConfigurationSection sec, String key, String def) {
        return (sec != null && sec.isString(key)) ? sec.getString(key, def) : def;
    }


    private static String normalizeSoundKey(String raw) {
        if (raw == null) return "minecraft:ui.button.click";
        final String s = raw.trim();
        if (s.isEmpty()) return "minecraft:ui.button.click";

        if (s.indexOf('.') >= 0) {
            return s.indexOf(':') >= 0 ? s : MINECRAFT_NS + s;
        }
        if (s.indexOf(':') >= 0) {
            return s;
        }
        final String dotted = s.toLowerCase(Locale.ROOT).replace('_', '.');
        return MINECRAFT_NS + dotted;
    }


    private static Sound legacyGuessEnum(String key, Sound def) {
        if (key == null) return def;
        final String k = key.toLowerCase(Locale.ROOT);
        // very small stable map for common UI sounds
        if (k.endsWith("ui.button.click")) return Sound.UI_BUTTON_CLICK;
        if (k.endsWith("entity.villager.no")) return Sound.ENTITY_VILLAGER_NO;
        if (k.endsWith("entity.experience_orb.pickup")) return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        if (k.endsWith("block.note_block.pling")) return Sound.BLOCK_NOTE_BLOCK_PLING;
        if (k.endsWith("block.note_block.bass")) return Sound.BLOCK_NOTE_BLOCK_BASS;
        return def;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
