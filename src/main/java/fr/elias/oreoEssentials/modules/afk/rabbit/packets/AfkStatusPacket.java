package fr.elias.oreoEssentials.modules.afk.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Broadcast packet sent when a player enters or leaves AFK on any server.
 * Used to power the cross-server Web UI and global AFK tracking.
 */
public class AfkStatusPacket extends Packet {

    private UUID playerId;
    private String playerName;
    private String server;
    private String worldName;
    private double x, y, z;
    private long afkSinceMs;
    private boolean entering;

    public AfkStatusPacket() {}

    public AfkStatusPacket(UUID playerId, String playerName, String server,
                           String worldName, double x, double y, double z,
                           long afkSinceMs, boolean entering) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.server = server;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.afkSinceMs = afkSinceMs;
        this.entering = entering;
    }

    public UUID getPlayerId()    { return playerId; }
    public String getPlayerName(){ return playerName; }
    public String getServer()    { return server; }
    public String getWorldName() { return worldName; }
    public double getX()         { return x; }
    public double getY()         { return y; }
    public double getZ()         { return z; }
    public long getAfkSinceMs()  { return afkSinceMs; }
    public boolean isEntering()  { return entering; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId != null ? playerId : new UUID(0L, 0L));
        out.writeString(playerName  != null ? playerName  : "");
        out.writeString(server      != null ? server      : "");
        out.writeString(worldName   != null ? worldName   : "");
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeLong(afkSinceMs);
        out.writeBoolean(entering);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        UUID id = in.readUUID();
        this.playerId   = (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L) ? null : id;
        this.playerName = in.readString();
        this.server     = in.readString();
        this.worldName  = in.readString();
        this.x          = in.readDouble();
        this.y          = in.readDouble();
        this.z          = in.readDouble();
        this.afkSinceMs = in.readLong();
        this.entering   = in.readBoolean();
    }
}
