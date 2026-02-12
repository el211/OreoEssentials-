package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

public final class PacketDefinition<T extends Packet> {
    private final long registryId;
    private final Class<T> packetClass;
    private final PacketProvider<T> provider;
    private final PacketNamespace namespace;

    public PacketDefinition(int registryId,
                            Class<T> packetClass,
                            PacketProvider<T> provider,
                            PacketNamespace namespace) {
        this.registryId = registryId;
        this.packetClass = packetClass;
        this.provider = provider;
        this.namespace = namespace;
    }

    public long getRegistryId()           { return registryId; }
    public Class<T> getPacketClass()      { return packetClass; }
    public PacketProvider<T> getProvider(){ return provider; }
    public PacketNamespace getNamespace() { return namespace; }
}
