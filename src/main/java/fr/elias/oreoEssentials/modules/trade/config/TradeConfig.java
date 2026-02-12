package fr.elias.oreoEssentials.modules.trade.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class TradeConfig {
    private final JavaPlugin plugin;

    public final boolean enabled;

    public final String guiTitle;
    public final int rows;

    public final Material dividerMaterial;
    public final String dividerName;
    public final boolean debugDeep;

    public final Material confirmMaterial;
    public final Material confirmedMaterial;
    public final String confirmText;
    public final String confirmedText;

    public final Material cancelMaterial;
    public final String cancelText;

    public final String youLabel;
    public final String themLabel;

    public final Sound openSound;
    public final Sound clickSound;
    public final Sound confirmSound;
    public final Sound cancelSound;
    public final Sound completeSound;

    public final boolean requireEmptyCursorOnConfirm;
    public final int minDistanceBlocks;

    public TradeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResource("trade.yml", false);
        FileConfiguration c = plugin.getConfig(); // main config
        org.bukkit.configuration.file.YamlConfiguration trade =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(plugin.getDataFolder(), "trade.yml")
                );

        this.guiTitle   = trade.getString("title", "&8Player Trade");
        this.rows       = Math.max(3, Math.min(6, trade.getInt("rows", 6)));

        this.dividerMaterial = safeMat(trade.getString("divider.material", "PURPLE_STAINED_GLASS_PANE"), Material.PURPLE_STAINED_GLASS_PANE);
        this.dividerName     = trade.getString("divider.name", "&5— Trade —");
        this.enabled = trade.getBoolean("enabled", true);   // <— NEW
        this.confirmMaterial   = safeMat(trade.getString("buttons.confirm.material", "EMERALD_BLOCK"), Material.EMERALD_BLOCK);
        this.confirmedMaterial = safeMat(trade.getString("buttons.confirmed.material", "LIME_CONCRETE"), Material.LIME_CONCRETE);
        this.confirmText       = trade.getString("buttons.confirm.text", "&aConfirm");
        this.confirmedText     = trade.getString("buttons.confirmed.text", "&aReady ✔");
        this.debugDeep = plugin.getConfig().getBoolean("tradedebug", false);

        this.cancelMaterial    = safeMat(trade.getString("buttons.cancel.material", "BARRIER"), Material.BARRIER);
        this.cancelText        = trade.getString("buttons.cancel.text", "&cCancel");

        this.youLabel          = trade.getString("labels.you", "&fYou");
        this.themLabel         = trade.getString("labels.them", "&fThem");

        this.openSound     = safeSound(trade.getString("sounds.open", "UI_BUTTON_CLICK"), Sound.UI_BUTTON_CLICK);
        this.clickSound    = safeSound(trade.getString("sounds.click", "UI_BUTTON_CLICK"), Sound.UI_BUTTON_CLICK);
        this.confirmSound  = safeSound(trade.getString("sounds.confirm", "BLOCK_NOTE_BLOCK_PLING"), Sound.BLOCK_NOTE_BLOCK_PLING);
        this.cancelSound   = safeSound(trade.getString("sounds.cancel", "ENTITY_VILLAGER_NO"), Sound.ENTITY_VILLAGER_NO);
        this.completeSound = safeSound(trade.getString("sounds.complete", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP);

        this.requireEmptyCursorOnConfirm = trade.getBoolean("require-empty-cursor-on-confirm", true);
        this.minDistanceBlocks           = Math.max(0, trade.getInt("min-distance-blocks", 0));
    }

    private static Material safeMat(String name, Material def) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Throwable t) { return def; }
    }
    private static Sound safeSound(String name, Sound def) {
        try { return Sound.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Throwable t) { return def; }
    }
}
