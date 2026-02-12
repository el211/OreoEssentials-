package fr.elias.oreoEssentials.modules.skin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SkinHookSR {
    private SkinHookSR() {}

    public static boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null
                || Bukkit.getPluginManager().getPlugin("SkinsRestorerX") != null;
    }
    public static boolean applySkinByName(Player player, String skinName) {
        try {
            Class<?> provider = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object applier = api.getClass().getMethod("getSkinApplier", Player.class).invoke(api, player);
            applier.getClass().getMethod("applySkin", Player.class, String.class).invoke(applier, player, skinName);
            return true;
        } catch (Throwable ignored) { return false; }
    }
}
