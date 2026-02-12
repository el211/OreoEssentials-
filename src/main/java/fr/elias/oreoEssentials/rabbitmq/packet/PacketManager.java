package fr.elias.oreoEssentials.rabbitmq.packet;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketDefinition;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketRegistry;
import fr.elias.oreoEssentials.rabbitmq.packet.event.IncomingPacketListener;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSender;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriptionQueue;
import fr.elias.oreoEssentials.modules.homes.rabbit.packet.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PacketManager implements IncomingPacketListener {

    private final OreoEssentials plugin;
    private final PacketSender sender;
    private final Map<Class<? extends Packet>, PacketSubscriptionQueue<? extends Packet>> subscriptions;
    private final PacketRegistry packetRegistry;
    private volatile boolean initialized;

    public PacketManager(OreoEssentials plugin, PacketSender sender) {
        this.plugin = plugin;
        this.sender = sender;
        this.subscriptions = new ConcurrentHashMap<>();
        this.packetRegistry = new PacketRegistry();
    }

    public void init() {
        initialized = true;
        this.sender.registerListener(this);
    }

    public void subscribeChannel(PacketChannel channel) {
        this.sender.registerChannel(channel);
    }

    public <T extends Packet> void registerPacket(Class<T> packetClass, Supplier<T> constructor) {
        try {
            packetRegistry.register(packetClass, constructor);
        } catch (NoSuchMethodError e) {
            boolean ok = tryReflectiveRegister(packetClass, constructor);
            if (!ok) {
                warn("[PM/REG] Failed to register packet " + packetClass.getName()
                        + " — PacketRegistry has no compatible register(...) method.");
            }
        } catch (Throwable t) {
            warn("[PM/REG] Error registering packet " + packetClass.getName() + ": " + t.getMessage());
        }
    }

    private <T extends Packet> boolean tryReflectiveRegister(Class<T> packetClass, Supplier<T> constructor) {
        String[] names = {"register", "define", "put"};
        for (String m : names) {
            try {
                var method = PacketRegistry.class.getMethod(m, Class.class, Supplier.class);
                method.invoke(packetRegistry, packetClass, constructor);
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public void sendPacket(PacketChannel target, Packet packet) {
        if (target == null) {
            warn("[PM/PUBLISH] target channel is null. packet=" + (packet == null ? "null" : packet.getClass().getName()));
            return;
        }
        if (packet == null) {
            warn("[PM/PUBLISH] packet is null. channel=" + renderChannel(target));
            return;
        }

        if (!initialized) {
            warn("[PM/PUBLISH] PacketManager not initialized (init() not called?)"
                    + " type=" + packet.getClass().getName()
                    + " channel=" + renderChannel(target));
        }

        PacketDefinition<?> definition = this.packetRegistry.getDefinition(packet.getClass());
        if (definition == null) {
            warn("[PM/PUBLISH] NO DEFINITION for type=" + packet.getClass().getName()
                    + " channel=" + renderChannel(target)
                    + " (Did you forget registerPacket(...) on this server?)");
            return;
        }

        FriendlyByteOutputStream out = new FriendlyByteOutputStream();
        out.writeLong(definition.getRegistryId());
        packet.writeData(out);

        try {
            this.sender.sendPacket(target, out.toByteArray());
        } catch (Throwable t) {
            warn("[PM/PUBLISH] sender.sendPacket FAILED for type="
                    + packet.getClass().getSimpleName()
                    + " id=" + definition.getRegistryId()
                    + " channel=" + renderChannel(target)
                    + " error=" + t.getMessage());
        }
    }

    public void sendPacket(Packet packet) {
        sendPacket(PacketChannels.GLOBAL, packet);
    }

    public <T extends Packet> void subscribe(Class<T> packetClass, PacketSubscriber<T> subscriber) {
        @SuppressWarnings("unchecked")
        PacketSubscriptionQueue<T> queue =
                (PacketSubscriptionQueue<T>) this.subscriptions.computeIfAbsent(
                        packetClass, key -> new PacketSubscriptionQueue<>(packetClass));
        queue.subscribe(subscriber);
    }

    @Override
    public void onReceive(PacketChannel channel, byte[] content) {
        if (channel == null) {
            warn("[PM/RECV] channel is null (contentBytes=" + (content == null ? -1 : content.length) + ")");
            return;
        }
        if (content == null || content.length < 8) {
            warn("[PM/RECV] content is null/too small len=" + (content == null ? -1 : content.length)
                    + " channel=" + renderChannel(channel));
            return;
        }

        try {
            FriendlyByteInputStream in = new FriendlyByteInputStream(content);
            long registryId = in.readLong();

            PacketDefinition<?> definition = this.packetRegistry.getDefinition(registryId);

            if (definition == null) {
                warn("[PM/RECV] Unknown registry id: " + registryId
                        + " bytes=" + content.length
                        + " channel=" + renderChannel(channel)
                        + " (Packet ids must match across servers; check register order & missing registerPacket calls)");
                return;
            }

            Packet packet = definition.getProvider().createPacket();
            packet.readData(in);

            dispatch(channel, packet);

        } catch (Throwable t) {
            warn("[PM/RECV] Failed to decode packet. err=" + t.getMessage()
                    + " channel=" + renderChannel(channel)
                    + " contentLen=" + content.length);
        }
    }

    private <T extends Packet> void dispatch(PacketChannel channel, T packet) {
        @SuppressWarnings("unchecked")
        PacketSubscriptionQueue<T> queue =
                (PacketSubscriptionQueue<T>) this.subscriptions.get(packet.getClass());

        if (queue == null) {
            warn("[PM/DISPATCH] No subscribers for " + packet.getClass().getName()
                    + " channel=" + renderChannel(channel)
                    + " (Did you forget packetManager.subscribe(PacketType.class, ...) on this server?)");
            return;
        }

        try {
            queue.dispatch(channel, packet);
        } catch (Throwable t) {
            warn("[PM/DISPATCH] Subscriber dispatch failed for type="
                    + packet.getClass().getSimpleName()
                    + " err=" + t.getMessage()
                    + " channel=" + renderChannel(channel));
        }
    }

    private int safeSubscribersCount(PacketSubscriptionQueue<?> queue) {
        try {
            var m = queue.getClass().getMethod("size");
            Object v = m.invoke(queue);
            if (v instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private String renderChannel(PacketChannel ch) {
        try {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String id : ch) {
                if (!first) sb.append(',');
                sb.append(id);
                first = false;
            }
            return sb.append(']').toString();
        } catch (Throwable t) {
            return ch.toString();
        }
    }

    private String serverName() {
        try {
            return plugin.getConfigService().serverName();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private void warn(String msg) {
        plugin.getLogger().warning(msg);
    }

    private void dbg(String msg) {
        if (isDebug()) plugin.getLogger().info(msg);
    }

    private boolean isDebug() {
        try {
            return plugin.getConfig().getBoolean("debug", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public PacketRegistry getPacketRegistry() {
        return packetRegistry;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void close() {
        initialized = false;
        sender.close();
    }

    public String registryChecksum() {
        try {
            return Integer.toHexString(packetRegistry.hashCode());
        } catch (Throwable t) {
            return "n/a";
        }
    }
}