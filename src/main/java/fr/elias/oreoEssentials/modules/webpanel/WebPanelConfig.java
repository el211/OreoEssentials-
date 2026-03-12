package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;

public class WebPanelConfig {

    private final boolean enabled;
    private final String apiKey;
    private final String amqpUri;       // from rabbitmq.uri — empty if rabbitmq.enabled=false
    private final boolean amqpEnabled;

    private static final String API_URL   = "http://88.99.150.35:19150";
    private static final String PANEL_URL = "http://88.99.150.35:19149/panel-dashboard.html";

    public WebPanelConfig(OreoEssentials plugin) {
        this.enabled      = plugin.getConfig().getBoolean("web-panel.enabled", false);
        this.apiKey       = plugin.getConfig().getString("web-panel.api-key", "").trim();
        this.amqpEnabled  = plugin.getConfig().getBoolean("rabbitmq.enabled", false);
        this.amqpUri      = amqpEnabled
                ? plugin.getConfig().getString("rabbitmq.uri", "").trim()
                : "";
    }

    public boolean isEnabled()     { return enabled && !apiKey.isEmpty(); }
    public String  getUrl()        { return API_URL; }
    public String  getPanelUrl()   { return PANEL_URL; }
    public String  getApiKey()     { return apiKey; }

    /** True when RabbitMQ is enabled AND a URI is set — use AMQP for sync instead of HTTP. */
    public boolean isAmqpEnabled() { return amqpEnabled && !amqpUri.isEmpty(); }
    public String  getAmqpUri()    { return amqpUri; }
}
