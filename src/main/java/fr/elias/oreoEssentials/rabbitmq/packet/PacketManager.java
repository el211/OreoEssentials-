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
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
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
        dbg("[PM/INIT@" + serverName() + "] PacketManager initialized and listener registered."
                + " debug=" + isDebug()
                + " registryChecksum=" + registryChecksum());
    }

    public void subscribeChannel(PacketChannel channel) {
        this.sender.registerChannel(channel);
        dbg("[PM/CHAN@" + serverName() + "] Subscribed channel " + renderChannel(channel)
                + " (registryChecksum=" + registryChecksum() + ")");
    }

    public <T extends Packet> void registerPacket(Class<T> packetClass, Supplier<T> constructor) {
        try {
            packetRegistry.register(packetClass, constructor);
            var def = packetRegistry.getDefinition(packetClass);
            long id = (def != null ? def.getRegistryId() : -1L);
            dbg("[PM/REG@" + serverName() + "] " + packetClass.getName() + " id=" + id
                    + " (registryChecksum=" + registryChecksum() + ")");
        } catch (NoSuchMethodError e) {
            boolean ok = tryReflectiveRegister(packetClass, constructor);
            if (!ok) {
                warn("[PM/REG@" + serverName() + "] Failed to register packet " + packetClass.getName()
                        + " — PacketRegistry has no compatible register(...) method.");
            } else {
                var def = packetRegistry.getDefinition(packetClass);
                long id = (def != null ? def.getRegistryId() : -1L);
                dbg("[PM/REG@" + serverName() + "/reflect] " + packetClass.getName() + " id=" + id
                        + " (registryChecksum=" + registryChecksum() + ")");
            }
        } catch (Throwable t) {
            warn("[PM/REG@" + serverName() + "] Error registering packet " + packetClass.getName() + ": " + t.getMessage());
        }
    }

    private <T extends Packet> boolean tryReflectiveRegister(Class<T> packetClass, Supplier<T> constructor) {
        String[] names = { "register", "define", "put" };
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
            warn("[PM/PUBLISH@" + serverName() + "] target channel is null. packet=" + (packet == null ? "null" : packet.getClass().getName()));
            return;
        }
        if (packet == null) {
            warn("[PM/PUBLISH@" + serverName() + "] packet is null. channel=" + renderChannel(target));
            return;
        }

        if (!initialized) {
            warn("[PM/PUBLISH@" + serverName() + "] PacketManager not initialized (init() not called?)"
                    + " type=" + packet.getClass().getName()
                    + " channel=" + renderChannel(target));
        }

        PacketDefinition<?> definition = this.packetRegistry.getDefinition(packet.getClass());
        if (definition == null) {
            warn("[PM/PUBLISH@" + serverName() + "] NO DEFINITION for type=" + packet.getClass().getName()
                    + " channel=" + renderChannel(target)
                    + " registryChecksum=" + registryChecksum()
                    + " (Did you forget registerPacket(...) on this server?)");
            return;
        }

        FriendlyByteOutputStream out = new FriendlyByteOutputStream();
        out.writeLong(definition.getRegistryId());
        packet.writeData(out);

        dbg("[PM/PUBLISH@" + serverName() + "] id=" + definition.getRegistryId()
                + " type=" + packet.getClass().getSimpleName()
                + " bytes=" + out.toByteArray().length
                + " channel=" + renderChannel(target)
                + " registryChecksum=" + registryChecksum());

        // Extra detail for homes (debug-only)
        if (packet instanceof HomeTeleportRequestPacket h) {
            dbg("[PM/PUBLISH@" + serverName() + "/HOME]"
                    + " requestId=" + h.getRequestId()
                    + " player=" + h.getPlayerId()
                    + " home=" + h.getHomeName()
                    + " target=" + h.getTargetServer()
                    + " channel=" + renderChannel(target));
        }

        try {
            this.sender.sendPacket(target, out.toByteArray());
        } catch (Throwable t) {
            warn("[PM/PUBLISH@" + serverName() + "] sender.sendPacket FAILED for type="
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
        dbg("[PM/SUB@" + serverName() + "] Subscribed " + packetClass.getSimpleName()
                + " subsCount=" + safeSubscribersCount(queue)
                + " (registryChecksum=" + registryChecksum() + ")");
    }

    @Override
    public void onReceive(PacketChannel channel, byte[] content) {
        if (channel == null) {
            warn("[PM/RECV@" + serverName() + "] channel is null (contentBytes=" + (content == null ? -1 : content.length) + ")");
            return;
        }
        if (content == null || content.length < 8) {
            warn("[PM/RECV@" + serverName() + "] content is null/too small len=" + (content == null ? -1 : content.length)
                    + " channel=" + renderChannel(channel));
            return;
        }

        try {
            FriendlyByteInputStream in = new FriendlyByteInputStream(content);
            long registryId = in.readLong();

            PacketDefinition<?> definition = this.packetRegistry.getDefinition(registryId);

            dbg("[PM/RECV@" + serverName() + "] registryId=" + registryId
                    + " type=" + (definition != null ? definition.getPacketClass().getSimpleName() : "unknown")
                    + " bytes=" + content.length
                    + " channel=" + renderChannel(channel)
                    + " registryChecksum=" + registryChecksum());

            if (definition == null) {
                warn("[PM/RECV@" + serverName() + "] Unknown registry id: " + registryId
                        + " bytes=" + content.length
                        + " channel=" + renderChannel(channel)
                        + " registryChecksum=" + registryChecksum()
                        + " (Packet ids must match across servers; check register order & missing registerPacket calls)");
                return;
            }

            Packet packet = definition.getProvider().createPacket();
            packet.readData(in);

            if (packet instanceof HomeTeleportRequestPacket h) {
                dbg("[PM/RECV@" + serverName() + "/HOME]"
                        + " requestId=" + h.getRequestId()
                        + " player=" + h.getPlayerId()
                        + " home=" + h.getHomeName()
                        + " target=" + h.getTargetServer()
                        + " channel=" + renderChannel(channel));
            }

            dispatch(channel, packet);

        } catch (Throwable t) {
            warn("[PM/RECV@" + serverName() + "] Failed to decode packet. err=" + t.getMessage()
                    + " channel=" + renderChannel(channel)
                    + " contentLen=" + content.length);
        }
    }

    private <T extends Packet> void dispatch(PacketChannel channel, T packet) {
        @SuppressWarnings("unchecked")
        PacketSubscriptionQueue<T> queue =
                (PacketSubscriptionQueue<T>) this.subscriptions.get(packet.getClass());

        if (queue == null) {
            warn("[PM/DISPATCH@" + serverName() + "] No subscribers for " + packet.getClass().getName()
                    + " channel=" + renderChannel(channel)
                    + " (Did you forget packetManager.subscribe(PacketType.class, ...) on this server?)");
            return;
        }

        dbg("[PM/DISPATCH@" + serverName() + "] Dispatching type=" + packet.getClass().getSimpleName()
                + " channel=" + renderChannel(channel)
                + " subsCount=" + safeSubscribersCount(queue));

        try {
            queue.dispatch(channel, packet);
        } catch (Throwable t) {
            warn("[PM/DISPATCH@" + serverName() + "] Subscriber dispatch failed for type="
                    + packet.getClass().getSimpleName()
                    + " err=" + t.getMessage()
                    + " channel=" + renderChannel(channel));
        }
    }

    private int safeSubscribersCount(PacketSubscriptionQueue<?> queue) {
        try {
            // If your PacketSubscriptionQueue has a size() method, use it.
            // If not, this will just fall back to -1 without breaking compilation.
            var m = queue.getClass().getMethod("size");
            Object v = m.invoke(queue);
            if (v instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}
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

    private void warn(String msg) { plugin.getLogger().warning(msg); }
    private void dbg(String msg) { if (isDebug()) plugin.getLogger().info(msg); }
    private boolean isDebug() {
        try { return plugin.getConfig().getBoolean("debug", false); } catch (Throwable t) { return false; }
    }

    public PacketRegistry getPacketRegistry() { return packetRegistry; }

    public boolean isInitialized() { return initialized; }

    public void close() {
        initialized = false;
        sender.close();
        dbg("[PM/CLOSE@" + serverName() + "] PacketManager closed.");
    }

    public String registryChecksum() {
        try {
            // Simple, stable-ish debug value
            return Integer.toHexString(packetRegistry.hashCode());
        } catch (Throwable t) {
            return "n/a";
        }
    }

}
