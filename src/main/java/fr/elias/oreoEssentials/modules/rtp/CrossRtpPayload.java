package fr.elias.oreoEssentials.modules.rtp;

import java.util.UUID;

public final class CrossRtpPayload {

    public enum Kind { RTP }

    private String kind = "RTP";
    private UUID   player;
    private String world;
    private String targetServer;

    public String getKind()        { return kind; }
    public UUID getPlayer()        { return player; }
    public void setPlayer(UUID u)  { this.player = u; }

    public String getWorld()       { return world; }
    public void setWorld(String w) { this.world = w; }

    public String getTargetServer()             { return targetServer; }
    public void setTargetServer(String server)  { this.targetServer = server; }
}
