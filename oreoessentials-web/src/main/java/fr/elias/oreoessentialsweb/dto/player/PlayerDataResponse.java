package fr.elias.oreoessentialsweb.dto.player;

import java.time.Instant;
import java.util.Map;

/**
 * Generic player data response.
 *
 * The {@code data} map is intentionally open — it reflects whatever the
 * plugin pushes or what exists in the server's MongoDB. Common keys from
 * OreoEssentials include:
 *
 *   balance        — Vault balance (double)
 *   playtime       — total playtime in seconds (long)
 *   stats          — { kills, deaths, joinCount, ... }
 *   inventoryBase64 — Base64-encoded inventory (from PlayerSyncSnapshot)
 *   armorBase64     — Base64-encoded armor contents
 *   potionEffects  — list of { type, duration, amplifier }
 *   jobs           — list of { name, level, xp }
 *   orders         — list of active market orders
 *   lastSeen       — ISO-8601 timestamp
 */
public record PlayerDataResponse(
        Map<String, Object> data,
        Instant lastUpdatedAt
) {}
