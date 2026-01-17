package fr.elias.oreoEssentials.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class Uuids {
    private Uuids() {}

    public static UUID resolve(String input) {
        Player p = Bukkit.getPlayerExact(input);
        if (p != null) return p.getUniqueId();

        OfflinePlayer op = Bukkit.getOfflinePlayer(input);
        if (op != null && (op.isOnline() || op.hasPlayedBefore())) return op.getUniqueId();

        try { return UUID.fromString(input); } catch (IllegalArgumentException ignored) {}

        try {
            Class<?> apiClz = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClz.getMethod("getInstance").invoke(null);
            Object uuid = apiClz.getMethod("getUuidFor", String.class).invoke(api, input);
            if (uuid instanceof UUID u) return u;
        } catch (Throwable ignored) {}

        return null;
    }
}
