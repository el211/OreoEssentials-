package fr.elias.oreoEssentials.modules.webpanel;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RegisterRewardConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public RegisterRewardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File folder = new File(plugin.getDataFolder(), "oreopanel");
        folder.mkdirs();
        File file = new File(folder, "reward.yml");
        if (!file.exists()) {
            plugin.saveResource("oreopanel/reward.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream in = plugin.getResource("oreopanel/reward.yml");
        if (in != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    // ── Global ────────────────────────────────────────────────────────────────

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    public String guiTitle() {
        return c(config.getString("gui.title", "&6Register Reward"));
    }

    public int guiRows() {
        return Math.max(1, Math.min(6, config.getInt("gui.rows", 3)));
    }

    public Material fillMaterial() {
        try {
            return Material.valueOf(config.getString("gui.fill-material", "BLACK_STAINED_GLASS_PANE"));
        } catch (Exception e) {
            return Material.BLACK_STAINED_GLASS_PANE;
        }
    }

    // ── Logo ──────────────────────────────────────────────────────────────────

    public int logoSlot() { return config.getInt("logo.slot", 13); }
    public String logoMaterial() { return config.getString("logo.material", "NETHER_STAR"); }
    public String logoItemsAdder() { return config.getString("logo.itemsadder", null); }
    public String logoNexo() { return config.getString("logo.nexo", null); }
    public int logoCustomModelData() { return config.getInt("logo.custom-model-data", 0); }
    public String logoName() { return c(config.getString("logo.name", "&6Register Reward")); }
    public List<String> logoLore() { return colorList(config.getStringList("logo.lore")); }

    // ── Claim button ──────────────────────────────────────────────────────────

    public int claimSlot() { return config.getInt("claim-button.slot", 22); }

    public Material claimMaterial() {
        try {
            return Material.valueOf(config.getString("claim-button.material", "LIME_STAINED_GLASS_PANE"));
        } catch (Exception e) {
            return Material.LIME_STAINED_GLASS_PANE;
        }
    }

    public String claimName() {
        return c(config.getString("claim-button.name", "&aClaim Reward"));
    }

    public List<String> claimLore() {
        return colorList(config.getStringList("claim-button.lore"));
    }

    // ── Already-claimed button ────────────────────────────────────────────────

    public Material alreadyClaimedMaterial() {
        try {
            return Material.valueOf(config.getString("already-claimed.material", "RED_STAINED_GLASS_PANE"));
        } catch (Exception e) {
            return Material.RED_STAINED_GLASS_PANE;
        }
    }

    public String alreadyClaimedName() {
        return c(config.getString("already-claimed.name", "&cAlready Claimed"));
    }

    public List<String> alreadyClaimedLore() {
        return colorList(config.getStringList("already-claimed.lore"));
    }

    // ── Not-registered button ─────────────────────────────────────────────────

    public Material notRegisteredMaterial() {
        try {
            return Material.valueOf(config.getString("not-registered.material", "ORANGE_STAINED_GLASS_PANE"));
        } catch (Exception e) {
            return Material.ORANGE_STAINED_GLASS_PANE;
        }
    }

    public String notRegisteredName() {
        return c(config.getString("not-registered.name", "&6Not Linked"));
    }

    public List<String> notRegisteredLore() {
        return colorList(config.getStringList("not-registered.lore"));
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    public String msgClaimed()        { return c(config.getString("messages.claimed",        "&aClaimed!")); }
    public String msgAlreadyClaimed() { return c(config.getString("messages.already-claimed","&cAlready claimed.")); }
    public String msgNotRegistered()  { return c(config.getString("messages.not-registered", "&cNot linked.")); }
    public String msgNotEnabled()     { return c(config.getString("messages.not-enabled",    "&cDisabled.")); }
    public String msgNoPanel()        { return c(config.getString("messages.no-panel",       "&cPanel not configured.")); }

    // ── Rewards ───────────────────────────────────────────────────────────────

    public boolean moneyEnabled() { return config.getBoolean("rewards.money.enabled", false); }
    public double moneyAmount()   { return config.getDouble("rewards.money.amount", 0); }
    public List<?> itemRewards()  { return config.getList("rewards.items",    List.of()); }
    public List<?> commandRewards() { return config.getList("rewards.commands", List.of()); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String c(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    private static List<String> colorList(List<String> list) {
        return list.stream().map(RegisterRewardConfig::c).toList();
    }
}
