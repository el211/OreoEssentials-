package fr.elias.oreoessentialsweb.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Utility for converting OreoEssentials inventory data into a frontend-friendly format.
 *
 * OreoEssentials stores inventories as a Base64-encoded byte array via
 * BukkitObjectOutputStream (Java serialization). This binary format cannot be
 * safely deserialized server-side without a full Bukkit environment.
 *
 * Instead, the RECOMMENDED approach is to have the OreoEssentials plugin
 * include a pre-serialized JSON representation of the inventory in the sync
 * payload alongside the raw Base64 blob. The plugin can serialize each
 * ItemStack to JSON using PaperMC's ItemStack#serializeAsBytes or a custom
 * NBT mapper before pushing.
 *
 * This utility processes the JSON inventory format expected in the sync payload.
 *
 * Expected JSON structure in playerDataJson:
 * {
 *   "inventory": [
 *     { "slot": 0, "material": "DIAMOND_SWORD", "amount": 1, "displayName": "Sword of Doom",
 *       "lore": ["Line 1"], "enchantments": {"SHARPNESS": 5} },
 *     { "slot": 1, "material": "GOLDEN_APPLE", "amount": 64 },
 *     null,   // empty slot
 *     ...
 *   ],
 *   "armor": [
 *     { "slot": 0, "material": "NETHERITE_BOOTS", "amount": 1 },
 *     ...
 *   ]
 * }
 */
@Slf4j
public class InventoryDisplayUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses a JSON inventory array (36 slots) from the player data map.
     *
     * @param playerDataJson Full player data JSON string from sync payload
     * @return List of 36 slot maps (null entries represent empty slots)
     */
    public static List<Map<String, Object>> parseInventory(String playerDataJson) {
        return parseSlots(playerDataJson, "inventory", 36);
    }

    /**
     * Parses a JSON armor array (4 slots: boots, leggings, chestplate, helmet).
     */
    public static List<Map<String, Object>> parseArmor(String playerDataJson) {
        return parseSlots(playerDataJson, "armor", 4);
    }

    /**
     * Parses offhand slot.
     */
    public static Map<String, Object> parseOffhand(String playerDataJson) {
        List<Map<String, Object>> slots = parseSlots(playerDataJson, "offhand", 1);
        return slots.isEmpty() ? null : slots.get(0);
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseSlots(String json, String key, int expectedSize) {
        List<Map<String, Object>> result = new ArrayList<>(Collections.nCopies(expectedSize, null));
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode arr = root.get(key);
            if (arr == null || !arr.isArray()) return result;

            for (int i = 0; i < arr.size() && i < expectedSize; i++) {
                JsonNode node = arr.get(i);
                if (node == null || node.isNull()) continue;
                result.set(i, MAPPER.convertValue(node, Map.class));
            }
        } catch (Exception e) {
            log.warn("Failed to parse {} inventory from JSON: {}", key, e.getMessage());
        }
        return result;
    }
}
