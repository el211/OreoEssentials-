package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public abstract class PacketNamespace {

    private final short namespaceId;
    private final Set<PacketDefinition<?>> definitions =
            ConcurrentHashMap.newKeySet();

    private volatile boolean registered = false;

    protected PacketNamespace(short namespaceId) {
        this.namespaceId = namespaceId;
    }

    /**
     * Subclasses call this inside {@link #registerPackets()} to add packets.
     */
    protected final <T extends Packet> void registerPacket(
            int packetId,
            Class<T> packetClass,
            PacketProvider<T> provider
    ) {
        PacketDefinition<T> def = new PacketDefinition<>(packetId, packetClass, provider, this);
        definitions.add(def);
    }

    /**
     * Implementations must register all their packets here via {@link #registerPacket(...)}.
     * Do NOT call this directly; it's invoked lazily by {@link #ensureRegistered()}.
     */
    protected abstract void registerPackets();

    /**
     * Ensures {@link #registerPackets()} has been called exactly once.
     */
    public final void ensureRegistered() {
        if (!registered) {
            synchronized (this) {
                if (!registered) {
                    registerPackets();
                    registered = true;
                }
            }
        }
    }

    public final short getNamespaceId() {
        return namespaceId;
    }

    /**
     * Returns all definitions for this namespace.
     * Lazily triggers {@link #registerPackets()} on first call.
     */
    public final Collection<PacketDefinition<?>> getDefinitions() {
        ensureRegistered();
        return definitions;
    }

    /**
     * Convenience: register all definitions from this namespace into a registry.
     */
    public final void registerInto(PacketRegistry registry) {
        ensureRegistered();
        for (PacketDefinition<?> def : definitions) {
            registry.register(def);
        }
    }
}
