package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.namespace.impl.HomesPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.namespace.impl.SpawnPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.namespace.impl.WarpsPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.namespace.impl.CrossInvPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.namespace.impl.TradePacketNamespace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class PacketRegistry {

    private final Map<Class<? extends Packet>, PacketDefinition<? extends Packet>> classToDefinition =
            new ConcurrentHashMap<>();
    private final Map<Long, PacketDefinition<?>> idToDefinition =
            new ConcurrentHashMap<>();

    // Dynamic namespace for programmatic registrations
    private final PacketNamespace dynamicNamespace = new DynamicPacketNamespace();

    // Dynamic ids start above the current max (or 1000 as a safe floor)
    private final AtomicInteger nextDynamicId;

    public PacketRegistry() {
        // Built-ins (if any)
        registerDefaults();

        // Existing namespaces
        register(new HomesPacketNamespace());
        register(new WarpsPacketNamespace());
        register(new SpawnPacketNamespace());
        register(new TradePacketNamespace());

        // Cross-server inv/ec namespace
        register(new CrossInvPacketNamespace());

        // Compute starting id for dynamic packets
        int start = idToDefinition.keySet().stream()
                .mapToInt(k -> (int) (long) k)
                .max()
                .orElse(999) + 1;
        this.nextDynamicId = new AtomicInteger(start);
    }

    /* -------- Public API (existing) -------- */

    public <T extends Packet> void register(PacketDefinition<T> definition) {
        classToDefinition.put(definition.getPacketClass(), definition);
        idToDefinition.put(definition.getRegistryId(), definition);
    }

    public void register(PacketNamespace namespace) {
        namespace.registerInto(this);
    }

    public PacketDefinition<? extends Packet> getDefinition(Class<? extends Packet> packetClass) {
        return classToDefinition.get(packetClass);
    }

    public PacketDefinition<?> getDefinition(long registryId) {
        return idToDefinition.get(registryId);
    }

    /* --------  dynamic registration overload -------- */

    /**
     * Programmatically register a packet class with a constructor supplier.
     * If the class is already registered, this is a no-op.
     */
    public <T extends Packet> void register(Class<T> packetClass, Supplier<T> constructor) {
        @SuppressWarnings("unchecked")
        PacketDefinition<T> existing = (PacketDefinition<T>) classToDefinition.get(packetClass);
        if (existing != null) return;

        int id = nextDynamicId.getAndIncrement();

        // Wrap Supplier<T> into PacketProvider<T>
        PacketProvider<T> provider = constructor::get;

        PacketDefinition<T> def = new PacketDefinition<>(
                id,
                packetClass,
                provider,
                dynamicNamespace
        );

        classToDefinition.put(packetClass, def);
        idToDefinition.put((long) id, def);
    }

    /* -------- Internals -------- */

    private void registerDefaults() {
        for (PacketNamespace ns : BuiltinPacketNamespaces.getNamespaces()) {
            register(ns);
        }
    }
}
