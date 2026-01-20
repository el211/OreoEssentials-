package fr.elias.oreoEssentials.rabbitmq.packet.impl.tp;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class SafeZoneEnterPacket extends Packet {

    private UUID playerId;
    private String targetServer;

    private String worldName;
    private String regionName;

    public SafeZoneEnterPacket() {}

    public SafeZoneEnterPacket(UUID playerId, String targetServer, String worldName, String regionName) {
        this.playerId = playerId;
        this.targetServer = targetServer;
        this.worldName = worldName;
        this.regionName = regionName;
    }

    public UUID getPlayerId() { return playerId; }
    public String getTargetServer() { return targetServer; }
    public String getWorldName() { return worldName; }
    public String getRegionName() { return regionName; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId != null ? playerId : new UUID(0L, 0L));
        out.writeString(targetServer != null ? targetServer : "");
        out.writeString(worldName != null ? worldName : "");
        out.writeString(regionName != null ? regionName : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        UUID id = in.readUUID();
        this.playerId = (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L) ? null : id;

        this.targetServer = in.readString();
        this.worldName = in.readString();
        this.regionName = in.readString();
    }
}
