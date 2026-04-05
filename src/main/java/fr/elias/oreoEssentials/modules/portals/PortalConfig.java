package fr.elias.oreoEssentials.modules.portals;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Manages the portals/ config folder:
 *   plugins/OreoEssentials/portals/config.yml
 *   plugins/OreoEssentials/portals/portals.yml
 */
public final class PortalConfig {

    private final File folder;

    // Cached values
    private String serverName;
    private long cooldownMs;
    private int maxPortalVolume;
    private boolean allowKeepYawPitch;
    private boolean teleportAsync;
    private String defaultSound;
    private String defaultParticleType;
    private int defaultParticleCount;
    private boolean ambientEnabled;
    private String ambientParticleType;
    private int ambientParticleCount;
    private int ambientIntervalTicks;
    private Material wandMaterial;
    private String wandName;
    private List<String> wandLore;

    public PortalConfig(File pluginDataFolder) {
        this.folder = new File(pluginDataFolder, "portals");
        if (!folder.exists()) folder.mkdirs();

        saveDefault("config.yml");
        reload();
    }

    public void reload() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));

        serverName          = cfg.getString("server-name", "");
        cooldownMs          = cfg.getLong("cooldown-ms", 1000L);
        maxPortalVolume     = cfg.getInt("max-portal-volume", 100000);
        allowKeepYawPitch   = cfg.getBoolean("allow-keep-yaw-pitch", true);
        teleportAsync       = cfg.getBoolean("teleport-async", false);
        defaultSound        = cfg.getString("default-sound", "ENTITY_ENDERMAN_TELEPORT");
        defaultParticleType = cfg.getString("default-particles.type", "PORTAL");
        defaultParticleCount= cfg.getInt("default-particles.count", 20);
        ambientEnabled      = cfg.getBoolean("ambient-particles.enabled", false);
        ambientParticleType = cfg.getString("ambient-particles.type", "PORTAL");
        ambientParticleCount= cfg.getInt("ambient-particles.count", 3);
        ambientIntervalTicks= Math.max(1, cfg.getInt("ambient-particles.interval-ticks", 10));

        // Wand
        String matName = cfg.getString("wand.material", "BLAZE_ROD").toUpperCase(Locale.ROOT);
        try { wandMaterial = Material.valueOf(matName); }
        catch (Exception e) { wandMaterial = Material.BLAZE_ROD; }
        wandName = c(cfg.getString("wand.name", "&6&lPortal Wand"));
        wandLore = cfg.getStringList("wand.lore").stream()
                .map(this::c).collect(Collectors.toList());
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public File getFolder()             { return folder; }
    public File getPortalsFile()        { return new File(folder, "portals.yml"); }
    public String getServerName()       { return serverName; }
    public long getCooldownMs()         { return cooldownMs; }
    public int getMaxPortalVolume()     { return maxPortalVolume; }
    public boolean isAllowKeepYawPitch(){ return allowKeepYawPitch; }
    public boolean isTeleportAsync()    { return teleportAsync; }
    public String getDefaultSound()     { return defaultSound; }
    public String getDefaultParticleType()  { return defaultParticleType; }
    public int getDefaultParticleCount()    { return defaultParticleCount; }
    public boolean isAmbientEnabled()       { return ambientEnabled; }
    public String getAmbientParticleType()  { return ambientParticleType; }
    public int getAmbientParticleCount()    { return ambientParticleCount; }
    public int getAmbientIntervalTicks()    { return ambientIntervalTicks; }
    public Material getWandMaterial()       { return wandMaterial; }

    /** Constructs the wand ItemStack. */
    public ItemStack buildWand() {
        ItemStack wand = new ItemStack(wandMaterial);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(wandName);
            meta.setLore(wandLore);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveDefault(String name) {
        File target = new File(folder, name);
        if (target.exists()) return;
        try (InputStream in = getClass().getResourceAsStream("/portals/" + name)) {
            if (in != null) Files.copy(in, target.toPath());
        } catch (Exception e) {
            // ignore — folder already created
        }
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
