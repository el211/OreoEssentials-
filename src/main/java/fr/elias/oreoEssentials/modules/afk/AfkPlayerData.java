package fr.elias.oreoEssentials.modules.afk;

import java.util.UUID;

public record AfkPlayerData(
        UUID id,
        String name,
        String server,
        String world,
        double x,
        double y,
        double z,
        long afkSinceMs
) {}
