package fr.elias.oreoEssentials.modules.webpanel;

import fr.elias.oreoEssentials.OreoEssentials;

public class WebPanelConfig {

    private final boolean enabled;
    private final String apiKey;

    private static final String API_URL   = "http://88.99.150.35:19150";
    private static final String PANEL_URL = "http://88.99.150.35:19149/panel-dashboard.html";

    public WebPanelConfig(OreoEssentials plugin) {
        this.enabled = plugin.getConfig().getBoolean("web-panel.enabled", false);
        this.apiKey  = plugin.getConfig().getString("web-panel.api-key", "").trim();
    }

    public boolean isEnabled() {
        return enabled && !apiKey.isEmpty();
    }

    public String getUrl()      { return API_URL; }
    public String getPanelUrl() { return PANEL_URL; }
    public String getApiKey()   { return apiKey; }
}
