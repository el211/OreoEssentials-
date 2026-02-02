package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomesPacketNamespace;
import fr.elias.oreoEssentials.modules.spawn.rabbit.namespace.SpawnPacketNamespace;
import fr.elias.oreoEssentials.modules.warps.rabbit.namespace.WarpsPacketNamespace;
import fr.elias.oreoEssentials.modules.cross.rabbit.namespace.CrossInvPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.modules.trade.rabbit.namespace.TradePacketNamespace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class PacketRegistry {

    private final Map<Class<? extends Packet>, PacketDefinition<? extends Packet>> classToDefinition =
            new ConcurrentHashMap<>();
    private final Map<Long, PacketDefinition<?>> idToDefinition =
            new ConcurrentHashMap<>();

    private final PacketNamespace dynamicNamespace = new DynamicPacketNamespace();

    private final AtomicInteger nextDynamicId;

    public PacketRegistry() {
        registerDefaults();

        register(new HomesPacketNamespace());
        register(new WarpsPacketNamespace());
        register(new SpawnPacketNamespace());
        register(new TradePacketNamespace());

        register(new CrossInvPacketNamespace());

        int start = idToDefinition.keySet().stream()
                .mapToInt(k -> (int) (long) k)
                .max()
                .orElse(999) + 1;
        this.nextDynamicId = new AtomicInteger(start);
    }


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


    /**
     * Programmatically register a packet class with a constructor supplier.
     * If the class is already registered, this is a no-op.
     */
    public <T extends Packet> void register(Class<T> packetClass, Supplier<T> constructor) {
        @SuppressWarnings("unchecked")
        PacketDefinition<T> existing = (PacketDefinition<T>) classToDefinition.get(packetClass);
        if (existing != null) return;

        int id = nextDynamicId.getAndIncrement();

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


    private void registerDefaults() {
        for (PacketNamespace ns : BuiltinPacketNamespaces.getNamespaces()) {
            register(ns);
        }
    }
}
