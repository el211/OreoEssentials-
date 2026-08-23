package fr.elias.oreoEssentials.playersync;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.enderchest.EnderChestStorage;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class PlayerSyncService {
    private final OreoEssentials plugin;
    private final PlayerSyncStorage storage;
    private final PlayerSyncPrefsStore prefs;

    public PlayerSyncService(OreoEssentials plugin, PlayerSyncStorage storage, PlayerSyncPrefsStore prefs) {
        this.plugin = plugin;
        this.storage = storage;
        this.prefs = prefs;
    }

    /** Capture Bukkit player state. Must run on the player's owning thread/region. */
    public PlayerSyncSnapshot captureSnapshot(Player p) {
        PlayerSyncPrefs pr = prefs.get(p.getUniqueId());
        PlayerSyncSnapshot s = new PlayerSyncSnapshot();

        if (pr.inv) {
            s.inventory = p.getInventory().getContents();
            s.armor     = p.getInventory().getArmorContents();
            s.offhand   = p.getInventory().getItemInOffHand();
        } else {
            s.inventory = new ItemStack[0];
            s.armor     = new ItemStack[0];
            s.offhand   = null;
        }

        if (pr.xp) {
            s.level = Math.max(0, p.getLevel());
            s.exp   = Math.max(0f, Math.min(1f, p.getExp()));
        } else {
            s.level = 0;
            s.exp   = 0f;
        }

        s.health = Math.max(0.0, p.getHealth());
        s.food = Math.max(0, Math.min(20, p.getFoodLevel()));
        s.saturation = Math.max(0f, Math.min(20f, p.getSaturation()));
        s.potionData = pr.potions ? serializePotions(p.getActivePotionEffects()) : null;
        return s;
    }

    /** Storage-only operation; safe to run asynchronously. */
    public void saveSnapshot(UUID uuid, PlayerSyncSnapshot snapshot) {
        try {
            storage.save(uuid, snapshot);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SYNC] save failed for " + uuid + ": " + t.getMessage());
        }
    }

    /** Storage-only operation; safe to run asynchronously. */
    public PlayerSyncSnapshot loadSnapshot(UUID uuid) {
        try {
            return storage.load(uuid);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SYNC] load failed for " + uuid + ": " + t.getMessage());
            return null;
        }
    }

    /** Apply a previously loaded snapshot. Must run on the player's owning thread/region. */
    public void applySnapshot(Player p, PlayerSyncSnapshot s) {
        try {
            if (p == null || s == null || !p.isOnline()) return;
            PlayerSyncPrefs pr = prefs.get(p.getUniqueId());

            if (pr.inv) {
                if (s.inventory != null) {
                    ItemStack[] main = EnderChestStorage.clamp(s.inventory, 4);
                    p.getInventory().setContents(main);
                }
                if (s.armor != null) {
                    p.getInventory().setArmorContents(s.armor);
                }
                p.getInventory().setItemInOffHand(s.offhand);
            }

            if (pr.xp) {
                p.setLevel(Math.max(0, s.level));
                p.setExp(Math.max(0f, Math.min(1f, s.exp)));
            }

            if (pr.health) {
                AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
                double max = (maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0);
                double raw = s.health;
                double target = raw <= 0.0 ? max : Math.max(1.0, Math.min(max, raw));
                try {
                    p.setHealth(target);
                } catch (IllegalArgumentException ignored) {
                    p.setHealth(Math.min(max, p.getHealth()));
                }
            }

            if (pr.hunger) {
                int food = s.food;
                float sat = s.saturation;
                if (food <= 0 && sat <= 0f) {
                    p.setFoodLevel(20);
                    p.setSaturation(10f);
                } else {
                    p.setFoodLevel(Math.max(0, Math.min(20, food)));
                    p.setSaturation(Math.max(0f, Math.min(20f, sat)));
                }
            }

            if (pr.potions && s.potionData != null && !s.potionData.isEmpty()) {
                List<PotionEffect> effects = deserializePotions(s.potionData);
                for (PotionEffect active : p.getActivePotionEffects()) {
                    p.removePotionEffect(active.getType());
                }
                for (PotionEffect effect : effects) {
                    p.addPotionEffect(effect);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[SYNC] apply failed for " + (p != null ? p.getUniqueId() : "unknown") + ": " + t.getMessage());
        }
    }

    private static String serializePotions(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PotionEffect e : effects) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getType().getName())
              .append(':').append(e.getDuration())
              .append(':').append(e.getAmplifier())
              .append(':').append(e.isAmbient())
              .append(':').append(e.hasParticles())
              .append(':').append(e.hasIcon());
        }
        return sb.toString();
    }

    private static List<PotionEffect> deserializePotions(String data) {
        List<PotionEffect> list = new ArrayList<>();
        if (data == null || data.isEmpty()) return list;
        for (String entry : data.split(";")) {
            try {
                String[] parts = entry.split(":");
                if (parts.length < 6) continue;
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                if (type == null) continue;
                int duration  = Integer.parseInt(parts[1]);
                int amplifier = Integer.parseInt(parts[2]);
                boolean ambient   = Boolean.parseBoolean(parts[3]);
                boolean particles = Boolean.parseBoolean(parts[4]);
                boolean icon      = Boolean.parseBoolean(parts[5]);
                list.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            } catch (Exception ignored) {}
        }
        return list;
    }

    public PlayerSyncPrefsStore prefs() { return prefs; }
}
