package fr.elias.oreoEssentials.modules.skin;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SkinDebug {
    private static boolean enabled;
    private static OreoEssentials plugin;

    private SkinDebug() {}

    public static void init(OreoEssentials pl) {
        plugin = pl;
        enabled = pl.getConfig().getBoolean("debug.skins", false);
        if (enabled) log("Skin debug ENABLED");
    }

    public static boolean on() { return enabled; }

    public static void log(String msg) {
        if (!enabled) return;
        if (plugin != null) plugin.getLogger().info("[Skins] " + msg);
        else Bukkit.getLogger().info("[Skins] " + msg);
    }

    public static void p(Player p, String msg) {
        if (!enabled || p == null) return;
        p.sendMessage("§8[§bSkins§8] §7" + msg);
    }
}
