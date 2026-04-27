package fr.elias.oreoEssentials.modules.vanish.rabbit;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public final class VanishSyncPacket extends Packet {
    private UUID playerId;
    private boolean vanished;
    private String sourceServer;

    public VanishSyncPacket() {}

    public VanishSyncPacket(UUID playerId, boolean vanished, String sourceServer) {
        this.playerId = playerId;
        this.vanished = vanished;
        this.sourceServer = sourceServer;
    }

    @Override
    protected void read(FriendlyByteInputStream stream) {
        this.playerId = stream.readUUID();
        this.vanished = stream.readBoolean();
        this.sourceServer = stream.readString();
    }

    @Override
    protected void write(FriendlyByteOutputStream stream) {
        stream.writeUUID(playerId != null ? playerId : new UUID(0L, 0L));
        stream.writeBoolean(vanished);
        stream.writeString(sourceServer != null ? sourceServer : "");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isVanished() {
        return vanished;
    }

    public String getSourceServer() {
        return sourceServer;
    }
}
