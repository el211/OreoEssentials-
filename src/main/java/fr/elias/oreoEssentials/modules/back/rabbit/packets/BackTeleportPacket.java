package fr.elias.oreoEssentials.modules.back.rabbit.packets;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class BackTeleportPacket extends Packet {

    private UUID playerUuid;
    private String server;
    private String world;
    private double x, y, z;
    private float yaw, pitch;

    public BackTeleportPacket() {}

    public BackTeleportPacket(UUID playerUuid,
                              String server,
                              String world,
                              double x, double y, double z,
                              float yaw, float pitch) {
        this.playerUuid = playerUuid;
        this.server = server;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getServer()   { return server; }
    public String getWorld()    { return world; }
    public double getX()        { return x; }
    public double getY()        { return y; }
    public double getZ()        { return z; }
    public float getYaw()       { return yaw; }
    public float getPitch()     { return pitch; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerUuid);
        out.writeString(server != null ? server : "");
        out.writeString(world != null ? world : "");
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.playerUuid = in.readUUID();
        this.server     = in.readString();
        this.world      = in.readString();
        this.x          = in.readDouble();
        this.y          = in.readDouble();
        this.z          = in.readDouble();
        this.yaw        = in.readFloat();
        this.pitch      = in.readFloat();
    }

    @Override
    public String toString() {
        return "BackTeleportPacket{" +
                "playerUuid=" + playerUuid +
                ", server='" + server + '\'' +
                ", world='" + world + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                '}';
    }
}
