package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;

public class WebPanelConfig {

    private final boolean enabled;
    private final String apiKey;
    private final String apiUrl;
    private final String amqpUri;       // from rabbitmq.uri — empty if rabbitmq.enabled=false
    private final boolean amqpEnabled;

    private static final String PANEL_URL = "http://oreostudios.fr/panel-dashboard.html";

    public WebPanelConfig(OreoEssentials plugin) {
        this.enabled      = plugin.getConfig().getBoolean("web-panel.enabled", false);
        this.apiKey       = plugin.getConfig().getString("web-panel.api-key", "").trim();
        this.apiUrl       = plugin.getConfig().getString("web-panel.url", "https://oreostudios.fr").trim();
        this.amqpEnabled  = plugin.getConfig().getBoolean("rabbitmq.enabled", false);
        this.amqpUri      = amqpEnabled
                ? plugin.getConfig().getString("rabbitmq.uri", "").trim()
                : "";
    }

    public boolean isEnabled()     { return enabled && !apiKey.isEmpty(); }
    public String  getUrl()        { return apiUrl; }
    public String  getPanelUrl()   { return PANEL_URL; }
    public String  getApiKey()     { return apiKey; }

    /**
     * Extracts the prefix segment from the API key (format: oreo_PREFIX_secret).
     * Used to identify this server in RabbitMQ messages without sending the full key.
     */
    public String getApiKeyPrefix() {
        String[] parts = apiKey.split("_", 3);
        return parts.length >= 2 ? parts[1] : apiKey;
    }

    /** True when RabbitMQ is enabled AND a URI is set — use AMQP for sync instead of HTTP. */
    public boolean isAmqpEnabled() { return amqpEnabled && !amqpUri.isEmpty(); }
    public String  getAmqpUri()    { return amqpUri; }
}
