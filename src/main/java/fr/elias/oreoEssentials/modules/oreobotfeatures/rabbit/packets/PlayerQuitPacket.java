package fr.elias.oreoEssentials.modules.oreobotfeatures.rabbit.packets;


import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;
import java.util.UUID;

public class PlayerQuitPacket extends Packet {

    private UUID playerId;

    public PlayerQuitPacket() {
        this.playerId = null;
    }

    public PlayerQuitPacket(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    protected void read(FriendlyByteInputStream stream) {
        this.playerId = stream.readUUID();
    }

    @Override
    protected void write(FriendlyByteOutputStream stream) {
        stream.writeUUID(playerId);
    }
}

