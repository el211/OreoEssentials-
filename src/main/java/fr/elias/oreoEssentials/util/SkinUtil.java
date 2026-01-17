package fr.elias.oreoEssentials.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;

public final class SkinUtil {
    private SkinUtil() {}

    public static PlayerProfile onlineProfileOf(String name) {
        try {
            var p = Bukkit.getPlayerExact(name);
            if (p != null) {
                SkinDebug.log("onlineProfileOf: found online player " + name);
                return p.getPlayerProfile();
            }
            SkinDebug.log("onlineProfileOf: " + name + " not online");
            return null;
        } catch (Throwable t) {
            SkinDebug.log("onlineProfileOf threw: " + t.getMessage());
            return null;
        }
    }

    public static void copyTextures(PlayerProfile src, PlayerProfile dst) {
        if (src == null || dst == null) {
            SkinDebug.log("copyTextures: src/dst null");
            return;
        }
        try {
            PlayerTextures s = src.getTextures();
            PlayerTextures d = dst.getTextures();
            URL skin = s.getSkin();
            URL cape = s.getCape();

            if (skin != null) {
                d.setSkin(skin, s.getSkinModel()); // NOTE: use getSkinModel/setSkin(url, model)
                SkinDebug.log("copyTextures: applied skin url=" + skin);
            } else {
                SkinDebug.log("copyTextures: src skin is null");
            }

            if (cape != null) {
                d.setCape(cape);
                SkinDebug.log("copyTextures: applied cape url=" + cape);
            }

            dst.setTextures(d);
        } catch (Throwable t) {
            SkinDebug.log("copyTextures threw: " + t.getMessage());
        }
    }

    public static boolean setProfileName(PlayerProfile profile, String name) {
        try {
            if (profile == null || name == null) return false;
            var m = profile.getClass().getMethod("setName", String.class);
            m.invoke(profile, name);
            SkinDebug.log("setProfileName: set to " + name);
            return true;
        } catch (Throwable ignored) {
            SkinDebug.log("setProfileName: method not present on this server build");
            return false;
        }
    }

    public static boolean applyProfile(Player player, PlayerProfile newProfile) {
        try {
            var m = player.getClass().getMethod("setPlayerProfile", PlayerProfile.class);
            m.invoke(player, newProfile);
            SkinDebug.log("applyProfile: Player#setPlayerProfile succeeded");
            return true;
        } catch (NoSuchMethodException nsme) {
            SkinDebug.log("applyProfile: setPlayerProfile not present on this server");
            return false;
        } catch (Throwable t) {
            SkinDebug.log("applyProfile threw: " + t.getMessage());
            return false;
        }
    }
}
