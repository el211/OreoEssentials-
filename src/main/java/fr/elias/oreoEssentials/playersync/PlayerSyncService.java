package fr.elias.oreoEssentials.playersync;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.enderchest.EnderChestStorage;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PlayerSyncService {
    private final OreoEssentials plugin;
    private final PlayerSyncStorage storage;
    private final PlayerSyncPrefsStore prefs;

    public PlayerSyncService(OreoEssentials plugin, PlayerSyncStorage storage, PlayerSyncPrefsStore prefs) {
        this.plugin = plugin;
        this.storage = storage;
        this.prefs = prefs;
    }

    public void saveIfEnabled(Player p) {
        try {
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

            if (pr.health) {
                s.health = Math.max(0.0, p.getHealth());
            } else {
                s.health = p.getHealth();
            }

            if (pr.hunger) {
                s.food       = Math.max(0, Math.min(20, p.getFoodLevel()));
                s.saturation = Math.max(0f, Math.min(20f, p.getSaturation()));
            } else {
                s.food       = p.getFoodLevel();
                s.saturation = p.getSaturation();
            }

            storage.save(p.getUniqueId(), s);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SYNC] save failed for " + p.getUniqueId() + ": " + t.getMessage());
        }
    }

    public void loadAndApply(Player p) {
        try {
            PlayerSyncPrefs pr = prefs.get(p.getUniqueId());
            PlayerSyncSnapshot s = storage.load(p.getUniqueId());
            if (s == null) return;

            if (pr.inv) {
                if (s.inventory != null) {
                    ItemStack[] main = EnderChestStorage.clamp(s.inventory, 4); // 4 rows = 36 slots
                    p.getInventory().setContents(main);
                }
                if (s.armor != null) {
                    p.getInventory().setArmorContents(s.armor);
                }
                p.getInventory().setItemInOffHand(s.offhand);
            }

            // XP
            if (pr.xp) {
                p.setLevel(Math.max(0, s.level));
                p.setExp(Math.max(0f, Math.min(1f, s.exp)));
            }

            if (pr.health) {
                AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
                double max = (maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0);

                double raw = s.health;
                double target;

                if (raw <= 0.0) {
                    target = max;
                } else {
                    target = Math.max(1.0, Math.min(max, raw));
                }

                try {
                    p.setHealth(target);
                } catch (IllegalArgumentException ignored) {
                    p.setHealth(Math.min(max, p.getHealth()));
                }
            }

            // Hunger
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
        } catch (Throwable t) {
            plugin.getLogger().warning("[SYNC] load/apply failed for " + p.getUniqueId() + ": " + t.getMessage());
        }
    }


    public PlayerSyncPrefsStore prefs() { return prefs; }
}
