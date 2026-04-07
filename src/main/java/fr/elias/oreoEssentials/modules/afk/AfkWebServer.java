package fr.elias.oreoEssentials.modules.afk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Lightweight embedded HTTP server that exposes AFK player data for the Web UI.
 * <p>
 * Endpoints:
 *   GET /         → HTML dashboard (auto-refreshes every 10 s)
 *   GET /api/afk  → JSON array of all currently AFK players (local + cross-server)
 */
@SuppressWarnings("restriction")
public class AfkWebServer {

    private final HttpServer httpServer;
    private final Map<String, AfkPlayerData> afkPlayers;
    private final Logger logger;

    public AfkWebServer(int port, Map<String, AfkPlayerData> afkPlayers, Logger logger) throws IOException {
        this.afkPlayers = afkPlayers;
        this.logger = logger;
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.httpServer.createContext("/api/afk", this::handleApi);
        this.httpServer.createContext("/", this::handleUi);
        this.httpServer.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OreoEssentials-AfkWeb");
            t.setDaemon(true);
            return t;
        }));
    }

    public void start() {
        httpServer.start();
        logger.info("[AfkWeb] HTTP server started on port " + httpServer.getAddress().getPort());
    }

    public void stop() {
        httpServer.stop(0);
        logger.info("[AfkWeb] HTTP server stopped.");
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handleApi(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            ex.sendResponseHeaders(405, -1);
            return;
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        long now = System.currentTimeMillis();

        for (AfkPlayerData d : afkPlayers.values()) {
            if (!first) json.append(",");
            first = false;

            long secs = Math.max(0, (now - d.afkSinceMs()) / 1000L);
            json.append("{")
                    .append("\"uuid\":\"").append(esc(d.id().toString())).append("\",")
                    .append("\"name\":\"").append(esc(d.name())).append("\",")
                    .append("\"server\":\"").append(esc(d.server())).append("\",")
                    .append("\"world\":\"").append(esc(d.world())).append("\",")
                    .append("\"x\":").append(String.format("%.1f", d.x())).append(",")
                    .append("\"y\":").append(String.format("%.1f", d.y())).append(",")
                    .append("\"z\":").append(String.format("%.1f", d.z())).append(",")
                    .append("\"afkSinceMs\":").append(d.afkSinceMs()).append(",")
                    .append("\"afkSeconds\":").append(secs)
                    .append("}");
        }

        json.append("]");
        sendResponse(ex, 200, "application/json", json.toString());
    }

    private void handleUi(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        sendResponse(ex, 200, "text/html; charset=utf-8", buildHtml());
    }

    // -------------------------------------------------------------------------
    // HTML page
    // -------------------------------------------------------------------------

    private String buildHtml() {
        return "<!DOCTYPE html>" +
               "<html lang='en'><head>" +
               "<meta charset='UTF-8'>" +
               "<meta http-equiv='refresh' content='10'>" +
               "<title>AFK Players</title>" +
               "<style>" +
               "body{font-family:sans-serif;background:#1a1a2e;color:#e0e0e0;margin:0;padding:24px}" +
               "h1{margin:0 0 4px 0;font-size:1.6rem;color:#a3c4f3}" +
               ".subtitle{color:#888;font-size:.85rem;margin-bottom:20px}" +
               "table{width:100%;border-collapse:collapse;background:#16213e;border-radius:8px;overflow:hidden}" +
               "th{background:#0f3460;color:#a3c4f3;padding:10px 14px;text-align:left;font-size:.85rem;text-transform:uppercase;letter-spacing:.05em}" +
               "td{padding:10px 14px;border-bottom:1px solid #1e2a45;font-size:.9rem}" +
               "tr:last-child td{border-bottom:none}" +
               "tr:hover td{background:#1e2a45}" +
               ".badge{display:inline-block;padding:2px 8px;border-radius:12px;font-size:.75rem;background:#0f3460;color:#a3c4f3}" +
               ".empty{text-align:center;padding:40px;color:#555}" +
               "</style>" +
               "</head><body>" +
               "<h1>AFK Players</h1>" +
               "<p class='subtitle'>Auto-refreshes every 10 seconds &bull; " + afkPlayers.size() + " player(s) currently AFK</p>" +
               "<div id='root'></div>" +
               "<script>" +
               "function fmt(s){" +
               "let d=Math.floor(s/86400),h=Math.floor(s%86400/3600),m=Math.floor(s%3600/60),sec=s%60;" +
               "let p=[];" +
               "if(d)p.push(d+'d');" +
               "if(h)p.push(h+'h');" +
               "if(m)p.push(m+'m');" +
               "p.push(sec+'s');" +
               "return p.join(' ');" +
               "}" +
               "function render(data){" +
               "let root=document.getElementById('root');" +
               "if(!data.length){root.innerHTML=\"<div class='empty'>No players are currently AFK.</div>\";return;}" +
               "let now=Date.now();" +
               "let html=\"<table><thead><tr><th>Player</th><th>Server</th><th>World</th><th>Position</th><th>AFK For</th></tr></thead><tbody>\";" +
               "data.forEach(p=>{" +
               "let secs=Math.floor((now-p.afkSinceMs)/1000);" +
               "html+=`<tr><td>${p.name}</td><td><span class='badge'>${p.server}</span></td><td>${p.world}</td><td>${p.x}, ${p.y}, ${p.z}</td><td>${fmt(secs)}</td></tr>`;" +
               "});" +
               "html+='</tbody></table>';" +
               "root.innerHTML=html;" +
               "}" +
               "fetch('/api/afk').then(r=>r.json()).then(render).catch(e=>console.error(e));" +
               "</script>" +
               "</body></html>";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendResponse(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
