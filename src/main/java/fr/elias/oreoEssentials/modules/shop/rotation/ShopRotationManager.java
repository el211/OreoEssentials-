package fr.elias.oreoEssentials.modules.shop.rotation;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.Shop;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages daily rotating item selections for shops.
 *
 * Persistence: one YAML file per rotating shop under
 *   plugins/OreoEssentials/shop/rotations/<shopId>.yml
 *
 * Thread-safety: all public methods are safe to call from the main thread.
 * The map is ConcurrentHashMap so reads from async tasks won't deadlock,
 * but writes (generate + save) should only happen on the main thread.
 */
public final class ShopRotationManager {

    private final ShopModule module;
    private final Logger     log;
    private final File       rotationsFolder;

    /** shopId → active rotation (in-memory cache) */
    private final Map<String, ShopRotation> rotations = new ConcurrentHashMap<>();

    public ShopRotationManager(ShopModule module) {
        this.module          = module;
        this.log             = module.getPlugin().getLogger();
        this.rotationsFolder = new File(module.getPlugin().getDataFolder(), "shop/rotations");
        rotationsFolder.mkdirs();
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    /**
     * Called once after all shops are loaded.
     * Loads saved rotations from disk; generates fresh ones where needed.
     */
    public void loadAll(Map<String, Shop> shops) {
        rotations.clear();
        for (Shop shop : shops.values()) {
            if (!shop.isRotating()) continue;

            ShopRotation saved = loadFromDisk(shop.getId());
            if (saved != null && !saved.isExpired() && hasValidItems(shop, saved)) {
                rotations.put(shop.getId(), saved);
                log.info("[Shop/Rotation] Loaded rotation for '" + shop.getId()
                        + "' — " + saved.getActiveItemIds().size() + " item(s)"
                        + ", resets at " + new Date(saved.getNextResetMs()));
            } else {
                ShopRotation fresh = generateAndSave(shop);
                rotations.put(shop.getId(), fresh);
                log.info("[Shop/Rotation] " + (saved == null ? "Generated" : "Rerolled (expired)")
                        + " rotation for '" + shop.getId()
                        + "' — " + fresh.getActiveItemIds().size() + " item(s)"
                        + ", resets at " + new Date(fresh.getNextResetMs()));
            }
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns the set of active item IDs for a rotating shop.
     * Auto-regenerates if the current rotation has expired.
     * Returns {@code null} if the shop is not rotating (caller must guard with isRotating()).
     */
    public Set<String> getActiveItemIds(Shop shop) {
        if (!shop.isRotating()) return null;

        ShopRotation rotation = rotations.get(shop.getId());
        if (rotation == null || rotation.isExpired()) {
            rotation = generateAndSave(shop);
            rotations.put(shop.getId(), rotation);
            log.info("[Shop/Rotation] Auto-rerolled rotation for '" + shop.getId()
                    + "' — resets at " + new Date(rotation.getNextResetMs()));
        }
        return rotation.getActiveItemIds();
    }

    /**
     * Force-rerolls the rotation for a shop regardless of expiry.
     * Returns {@code false} if the shop is not found or not rotating.
     */
    public boolean forceReroll(Shop shop) {
        if (!shop.isRotating()) return false;
        ShopRotation fresh = generateAndSave(shop);
        rotations.put(shop.getId(), fresh);
        log.info("[Shop/Rotation] Force-rerolled rotation for '" + shop.getId()
                + "' — " + fresh.getActiveItemIds().size() + " item(s)"
                + ", resets at " + new Date(fresh.getNextResetMs()));
        return true;
    }

    // ── Generation ────────────────────────────────────────────────────────────

    private ShopRotation generateAndSave(Shop shop) {
        RotationConfig cfg = shop.getRotationConfig();

        // All known item IDs in the shop (only those still present in config)
        List<String> allIds = new ArrayList<>(shop.getItems().keySet());

        int count = Math.min(cfg.getDisplayCount(), allIds.size());

        // Fisher-Yates via Collections.shuffle — guaranteed no duplicates
        Collections.shuffle(allIds);
        Set<String> picked = new LinkedHashSet<>(allIds.subList(0, count));

        long nextReset = computeNextResetMs(cfg);
        ShopRotation rotation = new ShopRotation(shop.getId(), picked, nextReset);
        saveToDisk(rotation);
        return rotation;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private ShopRotation loadFromDisk(String shopId) {
        File file = rotationFile(shopId);
        if (!file.exists()) return null;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<String> ids  = cfg.getStringList("active-items");
            long nextReset    = cfg.getLong("next-reset", 0L);
            if (ids.isEmpty() || nextReset == 0L) return null;
            return new ShopRotation(shopId, new LinkedHashSet<>(ids), nextReset);
        } catch (Exception e) {
            log.warning("[Shop/Rotation] Failed to load rotation for '"
                    + shopId + "': " + e.getMessage());
            return null;
        }
    }

    private void saveToDisk(ShopRotation rotation) {
        File file = rotationFile(rotation.getShopId());
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("shop-id",      rotation.getShopId());
        cfg.set("active-items", new ArrayList<>(rotation.getActiveItemIds()));
        cfg.set("next-reset",   rotation.getNextResetMs());
        try {
            cfg.save(file);
        } catch (IOException e) {
            log.warning("[Shop/Rotation] Failed to save rotation for '"
                    + rotation.getShopId() + "': " + e.getMessage());
        }
    }

    private File rotationFile(String shopId) {
        return new File(rotationsFolder, shopId.toLowerCase() + ".yml");
    }

    // ── Validity guard ────────────────────────────────────────────────────────

    /**
     * Returns true only if every saved item ID still exists in the shop config.
     * Prevents phantom items from appearing after an admin removes items from the YAML.
     */
    private boolean hasValidItems(Shop shop, ShopRotation rotation) {
        for (String id : rotation.getActiveItemIds()) {
            if (!shop.getItems().containsKey(id)) {
                log.info("[Shop/Rotation] Saved rotation for '" + shop.getId()
                        + "' references removed item '" + id + "' — will regenerate.");
                return false;
            }
        }
        return true;
    }

    // ── Time calculation ──────────────────────────────────────────────────────

    private long computeNextResetMs(RotationConfig cfg) {
        ZoneId zone;
        try {
            zone = ZoneId.of(cfg.getTimezone());
        } catch (Exception e) {
            log.warning("[Shop/Rotation] Invalid timezone '" + cfg.getTimezone()
                    + "' — falling back to UTC.");
            zone = ZoneId.of("UTC");
        }

        LocalTime resetTime;
        try {
            resetTime = LocalTime.parse(cfg.getResetTime()); // expects "HH:mm"
        } catch (DateTimeParseException e) {
            log.warning("[Shop/Rotation] Invalid reset-time '" + cfg.getResetTime()
                    + "' — falling back to 00:00.");
            resetTime = LocalTime.MIDNIGHT;
        }

        ZonedDateTime now  = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().atTime(resetTime).atZone(zone);

        // If the reset time today is already past (or is right now), schedule for tomorrow
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }

        return next.toInstant().toEpochMilli();
    }
}
