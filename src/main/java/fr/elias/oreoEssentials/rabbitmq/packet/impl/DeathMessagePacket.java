package fr.elias.oreoEssentials.rabbitmq.packet.impl;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class DeathMessagePacket extends Packet {

    private UUID deadPlayerId;
    private String deadPlayerName;
    private String message;
    private String sourceServer;

    public DeathMessagePacket() { }

    public DeathMessagePacket(UUID deadPlayerId, String deadPlayerName, String message, String sourceServer) {
        this.deadPlayerId = deadPlayerId;
        this.deadPlayerName = deadPlayerName;
        this.message = message;
        this.sourceServer = sourceServer;
    }

    public UUID getDeadPlayerId()      { return deadPlayerId; }
    public String getDeadPlayerName()  { return deadPlayerName; }
    public String getMessage()         { return message; }
    public String getSourceServer()    { return sourceServer; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(deadPlayerId);
        out.writeString(deadPlayerName != null ? deadPlayerName : "");
        out.writeString(message != null ? message : "");
        out.writeString(sourceServer != null ? sourceServer : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.deadPlayerId   = in.readUUID();
        this.deadPlayerName = in.readString();
        this.message        = in.readString();
        this.sourceServer   = in.readString();
    }
}