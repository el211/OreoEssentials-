package fr.elias.oreoEssentials.modules.afk.rabbit.packets;

import fr.elias.oreoEssentials.modules.back.BackLocation;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class AfkPoolEnterPacket extends Packet {

    private UUID playerId;
    private BackLocation returnLocation;
    private String targetServer;

    public AfkPoolEnterPacket() {
    }

    public AfkPoolEnterPacket(UUID playerId, BackLocation returnLocation, String targetServer) {
        this.playerId = playerId;
        this.returnLocation = returnLocation;
        this.targetServer = targetServer;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public BackLocation getReturnLocation() {
        return returnLocation;
    }

    public String getTargetServer() {
        return targetServer;
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId != null ? playerId : new UUID(0L, 0L));
        out.writeString(targetServer != null ? targetServer : "");

        if (returnLocation != null) {
            out.writeBoolean(true);
            out.writeString(returnLocation.getServer() != null ? returnLocation.getServer() : "");
            out.writeString(returnLocation.getWorldName() != null ? returnLocation.getWorldName() : "");
            out.writeDouble(returnLocation.getX());
            out.writeDouble(returnLocation.getY());
            out.writeDouble(returnLocation.getZ());
            out.writeFloat(returnLocation.getYaw());
            out.writeFloat(returnLocation.getPitch());
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        UUID id = in.readUUID();
        this.playerId = (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L) ? null : id;

        this.targetServer = in.readString();

        boolean hasLocation = in.readBoolean();
        if (hasLocation) {
            String server = in.readString();
            String world = in.readString();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();

            this.returnLocation = new BackLocation(server, world, x, y, z, yaw, pitch);
        } else {
            this.returnLocation = null;
        }
    }
}
