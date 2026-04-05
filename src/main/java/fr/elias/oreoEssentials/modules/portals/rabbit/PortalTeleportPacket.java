package fr.elias.oreoEssentials.modules.portals.rabbit;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Sent from Server A to Server B when a cross-server portal is triggered.
 * Server B queues a teleport to (world, x, y, z) for the arriving player.
 */
public final class PortalTeleportPacket extends Packet {

    private UUID playerId;
    private String worldName;
    private double x, y, z;
    private float yaw, pitch;
    private boolean keepYawPitch;
    private String requestId;

    public PortalTeleportPacket() {}

    public PortalTeleportPacket(UUID playerId, String worldName,
                                double x, double y, double z,
                                float yaw, float pitch, boolean keepYawPitch) {
        this.playerId     = playerId;
        this.worldName    = worldName;
        this.x            = x;
        this.y            = y;
        this.z            = z;
        this.yaw          = yaw;
        this.pitch        = pitch;
        this.keepYawPitch = keepYawPitch;
        this.requestId    = UUID.randomUUID().toString();
    }

    public UUID getPlayerId()    { return playerId; }
    public String getWorldName() { return worldName; }
    public double getX()         { return x; }
    public double getY()         { return y; }
    public double getZ()         { return z; }
    public float getYaw()        { return yaw; }
    public float getPitch()      { return pitch; }
    public boolean isKeepYawPitch() { return keepYawPitch; }
    public String getRequestId() { return requestId; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId != null ? playerId : new UUID(0L, 0L));
        out.writeString(worldName != null ? worldName : "");
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
        out.writeBoolean(keepYawPitch);
        out.writeString(requestId != null ? requestId : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        UUID id = in.readUUID();
        this.playerId     = (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L) ? null : id;
        this.worldName    = in.readString();
        this.x            = in.readDouble();
        this.y            = in.readDouble();
        this.z            = in.readDouble();
        this.yaw          = in.readFloat();
        this.pitch        = in.readFloat();
        this.keepYawPitch = in.readBoolean();
        this.requestId    = in.readString();
    }
}
