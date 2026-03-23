package fr.elias.oreoEssentials.modules.webpanel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Lightweight HTTP client for the OreoStudios web panel API.
 * Uses the built-in HttpURLConnection to avoid adding extra dependencies.
 */
public class WebPanelClient {

    private final WebPanelConfig config;
    private final Logger log;

    public WebPanelClient(WebPanelConfig config, Logger log) {
        this.config = config;
        this.log    = log;
    }

    /**
     * Sends a POST to /api/v1/plugin/weblink/register with the generated code.
     *
     * @return true if the panel accepted the code (HTTP 200), false otherwise.
     */
    public boolean registerWebLink(String code, UUID uuid, String playerName) {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/weblink/register");
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);

            String body = String.format(
                    "{\"code\":\"%s\",\"uuid\":\"%s\",\"playerName\":\"%s\"}",
                    code, uuid, playerName.replace("\"", ""));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            conn.disconnect();

            if (status != 200) {
                log.warning("[WebPanel] /weblink/register returned HTTP " + status);
            }
            return status == 200;

        } catch (Exception e) {
            log.warning("[WebPanel] Failed to reach panel for weblink: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a POST to /api/v1/plugin/sync with a full player data snapshot.
     *
     * @param uuid       the player's Minecraft UUID
     * @param playerName the player's name
     * @param dataJson   the full player data as a JSON string
     * @return true if the panel accepted the sync (HTTP 200), false otherwise.
     */
    public boolean syncPlayer(UUID uuid, String playerName, String dataJson) {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/sync");
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);

            // Use Gson so playerDataJson is properly escaped as a JSON string value
            JsonObject outer = new JsonObject();
            outer.addProperty("playerUuid", uuid.toString());
            outer.addProperty("playerName", playerName);
            outer.addProperty("playerDataJson", dataJson);
            byte[] body = new Gson().toJson(outer).getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int status = conn.getResponseCode();
            conn.disconnect();
            if (status != 200) log.warning("[WebPanel] /sync returned HTTP " + status);
            return status == 200;

        } catch (Exception e) {
            log.warning("[WebPanel] Failed to sync player " + playerName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the given player UUID is linked to a website account.
     * Calls GET /api/v1/plugin/players/{uuid}/registered.
     *
     * @return true if the player has a linked website account, false otherwise.
     */
    public boolean isPlayerRegistered(UUID uuid) {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/players/" + uuid + "/registered");
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);

            int status = conn.getResponseCode();
            if (status != 200) {
                conn.disconnect();
                return false;
            }

            try (java.io.InputStream is = conn.getInputStream()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                conn.disconnect();
                // Parse {"registered":true/false}
                JsonObject obj = new Gson().fromJson(body, JsonObject.class);
                return obj != null && obj.has("registered") && obj.get("registered").getAsBoolean();
            }
        } catch (Exception e) {
            log.warning("[WebPanel] Failed to check registration for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Polls /api/v1/plugin/actions for pending SELL / DELETE actions.
     * Only actions for the given online player UUIDs are returned and marked processed,
     * so actions for offline senders are never silently discarded.
     *
     * @param onlinePlayerUuids UUIDs of all players currently online on this server
     * @return the parsed JSON object with an "actions" array, or null on failure.
     */
    public JsonObject pollPendingActions(java.util.Collection<String> onlinePlayerUuids) {
        try {
            StringBuilder url = new StringBuilder(config.getUrl() + "/api/v1/plugin/actions");
            if (onlinePlayerUuids != null && !onlinePlayerUuids.isEmpty()) {
                url.append("?onlinePlayers=").append(String.join(",", onlinePlayerUuids));
            }
            URL endpoint = new URL(url.toString());
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            int status = conn.getResponseCode();
            if (status != 200) return null;
            String body = new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            conn.disconnect();
            return new Gson().fromJson(body, JsonObject.class);
        } catch (Exception e) {
            log.warning("[WebPanel] Failed to poll actions: " + e.getMessage());
            return null;
        }
    }

    /**
     * Polls /api/v1/plugin/deliveries for items waiting to be given to this player.
     * Called on player login to deliver any queued items from web-panel Give actions.
     *
     * @param playerUuid the player's Minecraft UUID string
     * @return the parsed JSON object with a "deliveries" array, or null on failure.
     */
    public JsonObject pollPendingDeliveries(String playerUuid) {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/deliveries?playerUuid=" + playerUuid);
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            int status = conn.getResponseCode();
            if (status != 200) return null;
            String body = new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            conn.disconnect();
            return new Gson().fromJson(body, JsonObject.class);
        } catch (Exception e) {
            log.warning("[WebPanel] Failed to poll deliveries for " + playerUuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Confirms that deliveries have been given in-game so they are not re-delivered.
     *
     * @param deliveryIds list of delivery IDs to mark as delivered
     * @return true if the panel accepted the confirmation (HTTP 200).
     */
    public boolean confirmDeliveries(java.util.List<Long> deliveryIds) {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/deliveries/confirm");
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);

            // Build {"deliveryIds":[1,2,3]}
            StringBuilder sb = new StringBuilder("{\"deliveryIds\":[");
            for (int i = 0; i < deliveryIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(deliveryIds.get(i));
            }
            sb.append("]}");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            conn.disconnect();
            return status == 200;
        } catch (Exception e) {
            log.warning("[WebPanel] Failed to confirm deliveries: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pushes all active market orders for this server to the panel.
     * Called periodically so the web panel can show a live marketplace.
     *
     * @param ordersJson JSON array string of serialized orders
     * @return true if accepted (HTTP 200).
     */
    public boolean syncMarketOrders(String ordersJson) {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/orders/sync");
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);

            // Use Gson to safely wrap the JSON array in the outer object
            com.google.gson.JsonObject outer = new com.google.gson.JsonObject();
            outer.addProperty("ordersJson", ordersJson);
            byte[] body = new com.google.gson.Gson().toJson(outer).getBytes(java.nio.charset.StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int status = conn.getResponseCode();
            conn.disconnect();
            if (status != 200) log.warning("[WebPanel] /orders/sync returned HTTP " + status);
            return status == 200;
        } catch (Exception e) {
            log.warning("[WebPanel] Failed to sync market orders: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pushes a full snapshot of all LuckPerms groups and their permission nodes to the backend.
     * Called once on startup and after any manual reload so the panel always reflects reality.
     *
     * @param groupsJson JSON string: {"groups":[{"groupName":"admin","permissions":{"oreo.vault":true}},...]}
     * @return true if the panel accepted the payload (HTTP 200).
     */
    public boolean syncLuckPermsGroups(String groupsJson) {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/luckperms/sync");
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(groupsJson.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            conn.disconnect();
            if (status != 204) log.warning("[WebPanel] /luckperms/sync returned HTTP " + status);
            return status == 204;
        } catch (Exception e) {
            log.warning("[WebPanel] Failed to sync LuckPerms groups: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pings the panel to verify the API key works.
     * @return true if the panel responds with HTTP 200.
     */
    public boolean ping() {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/ping");
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Api-Key", config.getApiKey());
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            int status = conn.getResponseCode();
            conn.disconnect();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
