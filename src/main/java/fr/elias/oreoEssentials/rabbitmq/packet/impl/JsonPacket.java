package fr.elias.oreoEssentials.rabbitmq.packet.impl;

public final class JsonPacket {
    private final String json;
    public JsonPacket(String json) { this.json = json; }
    public String getJson() { return json; }
}
