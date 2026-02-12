package fr.elias.oreoEssentials.modules.skin;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class SkinRefresherPE implements SkinRefresher {
    private final OreoEssentials plugin;
    SkinRefresherPE(OreoEssentials plugin) { this.plugin = plugin; }

    @Override
    public void refresh(Player player) {
        SkinDebug.log("SkinRefresherPE.refresh called");
        if (player == null || !player.isOnline()) return;

        try {
            Object api = getPacketEventsAPI();
            if (api == null) { SkinDebug.log("PE: getAPI() returned null (no PE?)"); fallback(player); return; }

            Object pm = call(api, "getPlayerManager");
            if (pm == null) { SkinDebug.log("PE: getPlayerManager() returned null"); fallback(player); return; }

            if (tryV2(player, pm)) return;
            if (tryV1(player, pm)) return;

            SkinDebug.log("PE: no wrapper matched; falling back");
            fallback(player);
        } catch (Throwable t) {
            SkinDebug.log("PE refresh threw: " + t);
            fallback(player);
        }
    }


    private static Object getPacketEventsAPI() {
        Object api = reflectStatic("io.github.retrooper.packetevents.PacketEvents", "getAPI");
        if (api != null) return api;
        return reflectStatic("com.github.retrooper.packetevents.PacketEvents", "getAPI");
    }

    private static Object reflectStatic(String clazz, String method) {
        try { return Class.forName(clazz).getMethod(method).invoke(null); }
        catch (Throwable ignored) { return null; }
    }

    private static Object call(Object obj, String method) {
        try { return obj.getClass().getMethod(method).invoke(obj); }
        catch (Throwable ignored) { return null; }
    }

    private boolean tryV2(Player player, Object pm) {
        try {
            Class<?> cls = forNameEither(
                    "io.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove",
                    "com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove"
            );
            if (cls == null) { SkinDebug.log("PE v2: wrapper class not found"); return false; }

            Object wrapper = null;
            for (Constructor<?> c : cls.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 1 && pt[0].isArray() && pt[0].getComponentType() == UUID.class) {
                    wrapper = c.newInstance((Object) new UUID[]{player.getUniqueId()});
                    break;
                }
                if (pt.length == 1 && List.class.isAssignableFrom(pt[0])) {
                    wrapper = c.newInstance(Collections.singletonList(player.getUniqueId()));
                    break;
                }
            }
            if (wrapper == null) { SkinDebug.log("PE v2: could not construct remove packet"); return false; }

            Method sendM = pm.getClass().getMethod("sendPacket", Player.class, Object.class);
            for (Player v : Bukkit.getOnlinePlayers()) {
                try { sendM.invoke(pm, v, wrapper); } catch (Throwable ignored) {}
            }
            SkinDebug.log("PE v2: sent PlayerInfoRemove to all viewers");

            // show again to force re-add using fresh profile entry
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player v : Bukkit.getOnlinePlayers()) {
                    if (v.equals(player)) continue;
                    v.hidePlayer(plugin, player);
                    v.showPlayer(plugin, player);
                }
            });
            return true;
        } catch (Throwable t) {
            SkinDebug.log("PE v2 failed: " + t);
            return false;
        }
    }

    private boolean tryV1(Player player, Object pm) {
        try {
            Class<?> wrapCls = forNameEither(
                    "io.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo",
                    "com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo"
            );
            Class<?> actionEnum = forNameEither(
                    "io.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo$Action",
                    "com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo$Action"
            );
            if (wrapCls == null || actionEnum == null) { SkinDebug.log("PE v1: wrapper/action not found"); return false; }

            Object remove = null;
            for (Object e : actionEnum.getEnumConstants()) {
                String n = e.toString().toUpperCase(Locale.ROOT);
                if (n.contains("REMOVE")) { remove = e; break; }
            }
            if (remove == null) { SkinDebug.log("PE v1: REMOVE action not found"); return false; }

            Object packet = null;
            for (Constructor<?> c : wrapCls.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0] == actionEnum && pt[1].isArray() && pt[1].getComponentType() == UUID.class) {
                    packet = c.newInstance(remove, (Object) new UUID[]{player.getUniqueId()});
                    break;
                }
                if (pt.length == 2 && pt[0] == actionEnum && pt[1].isArray() && pt[1].getComponentType() == Player.class) {
                    packet = c.newInstance(remove, (Object) new Player[]{player});
                    break;
                }
            }
            if (packet == null) { SkinDebug.log("PE v1: could not construct packet"); return false; }

            Method send = pm.getClass().getMethod("sendPacket", Player.class, Object.class);
            for (Player v : Bukkit.getOnlinePlayers()) send.invoke(pm, v, packet);
            SkinDebug.log("PE v1: sent PlayerInfo REMOVE to all");

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player v : Bukkit.getOnlinePlayers()) {
                    if (v.equals(player)) continue;
                    v.hidePlayer(plugin, player);
                    v.showPlayer(plugin, player);
                }
            });
            return true;
        } catch (Throwable t) {
            SkinDebug.log("PE v1 failed: " + t);
            return false;
        }
    }

    private static Class<?> forNameEither(String a, String b) {
        try { return Class.forName(a); } catch (Throwable ignored) {}
        try { return Class.forName(b); } catch (Throwable ignored) {}
        return null;
    }

    private void fallback(Player player) {
        new SkinRefresherFallback().refresh(player);
    }
}
