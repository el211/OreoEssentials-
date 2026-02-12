package fr.elias.oreoEssentials.modules.cross.rabbit.packets;

import java.util.UUID;


public final class CrossModPacket {

    public enum Action {
        KILL,
        KICK,
        BAN,
        FREEZE_TOGGLE,
        VANISH_TOGGLE,
        GAMEMODE_CYCLE,
        HEAL,
        FEED
    }

    private String kind = "MOD";

    private Action action;
    private UUID   target;
    private String targetName;
    private String reason;
    private String sourceServer;
    private String targetServer; // optional

    public CrossModPacket() {}

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public UUID getTarget() { return target; }
    public void setTarget(UUID target) { this.target = target; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSourceServer() { return sourceServer; }
    public void setSourceServer(String sourceServer) { this.sourceServer = sourceServer; }

    public String getTargetServer() { return targetServer; }
    public void setTargetServer(String targetServer) { this.targetServer = targetServer; }
}
