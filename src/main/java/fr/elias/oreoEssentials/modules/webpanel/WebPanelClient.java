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
     * Polls /api/v1/plugin/actions for pending SELL / DELETE actions queued from the web panel.
     * Actions are drained server-side on each call.
     *
     * @return the parsed JSON object, or null on failure.
     */
    public JsonObject pollPendingActions() {
        try {
            URL endpoint = new URL(config.getUrl() + "/api/v1/plugin/actions");
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
