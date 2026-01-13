// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/DynamicPacketNamespace.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

public final class DynamicPacketNamespace extends PacketNamespace {

    public static final short ID = (short) 32000;

    public DynamicPacketNamespace() {
        super(ID); // pass the id to the base; do NOT override getNamespaceId()
    }

    @Override
    protected void registerPackets() {
        // Intentionally empty
    }
}
