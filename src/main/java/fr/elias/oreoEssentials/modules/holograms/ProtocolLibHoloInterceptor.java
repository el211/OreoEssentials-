// file: src/main/java/fr/elias/oreoEssentials/modules/holograms/ProtocolLibHoloInterceptor.java
package fr.elias.oreoEssentials.modules.holograms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Uses ProtocolLib to intercept outgoing ENTITY_METADATA packets for tracked
 * PAPI holograms and replace the text component with per-player PAPI-resolved text.
 *
 * Also listens for SPAWN_ENTITY packets so that the initial entity-data packet
 * sent when a player first enters view range (which may be bundled on Paper 1.21+)
 * is also handled via a direct ProtocolLib push.
 */
public final class ProtocolLibHoloInterceptor {

    /** Raw EntityDataAccessor<Component> for TextDisplay.DATA_TEXT_ID (debug/verification only). */
    private static final Object TEXT_ACCESSOR;

    /** Metadata index of the TEXT field in TextDisplay entities (typically 23). */
    private static final int TEXT_META_INDEX;

    /**
     * ProtocolLib DataWatcher serializer for chat components.
     * NOTE: WrappedDataValue expects ProtocolLib's serializer, not NMS EntityDataSerializer.
     */
    private static final WrappedDataWatcher.Serializer COMPONENT_SERIALIZER;

    static {
        Object accessor = null;
        int idx = -1;

        try {
            Class<?> clsTextDisplay = Class.forName("net.minecraft.world.entity.Display$TextDisplay");
            Field f = clsTextDisplay.getDeclaredField("DATA_TEXT_ID");
            f.setAccessible(true);
            accessor = f.get(null);

            boolean found = false;

            // Approach 1: record-component accessor method (MC 1.21.x uses record)
            for (String methodName : new String[]{"id", "getId", "getIndex", "index"}) {
                try {
                    Method m = accessor.getClass().getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    Object result = m.invoke(accessor);
                    if (result instanceof Number) {
                        idx = ((Number) result).intValue();
                        found = true;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }

            // Approach 2: scan no-arg int methods
            if (!found) {
                java.util.Set<String> skip = java.util.Set.of("hashCode", "ordinal");
                for (Method m : accessor.getClass().getDeclaredMethods()) {
                    if (m.getParameterCount() == 0
                            && (m.getReturnType() == int.class || m.getReturnType() == Integer.class)
                            && !skip.contains(m.getName())) {
                        try {
                            m.setAccessible(true);
                            idx = ((Number) m.invoke(accessor)).intValue();
                            found = true;
                            break;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            // Approach 3: scan int fields
            if (!found) {
                for (Field idField : accessor.getClass().getDeclaredFields()) {
                    if (idField.getType() == int.class) {
                        try {
                            idField.setAccessible(true);
                            idx = idField.getInt(accessor);
                            found = true;
                            break;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            if (!found) {
                idx = 23; // MC 1.21.x hardcoded fallback
                Bukkit.getLogger().warning("[OreoHolograms] ProtocolLib interceptor: reflection failed, using TEXT_META_INDEX=23");
            } else {
                Bukkit.getLogger().info("[OreoHolograms] ProtocolLib interceptor: TEXT_META_INDEX=" + idx);
            }

        } catch (Throwable t) {
            idx = 23;
            Bukkit.getLogger().warning("[OreoHolograms] ProtocolLib interceptor: static init failed (" + t + "), using fallback index 23");
        }

        TEXT_ACCESSOR = accessor;
        TEXT_META_INDEX = idx;

        WrappedDataWatcher.Serializer ser;
        try {
            // TextDisplay.DATA_TEXT_ID is Optional<Component> in MC 1.20+ — use the optional variant.
            ser = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
            Bukkit.getLogger().info("[OreoHolograms] ProtocolLib interceptor: COMPONENT_SERIALIZER=Registry.getChatComponentSerializer(true) [Optional]");
        } catch (Throwable t) {
            try {
                ser = WrappedDataWatcher.Registry.getChatComponentSerializer();
                Bukkit.getLogger().warning("[OreoHolograms] ProtocolLib interceptor: optional serializer unavailable, using non-optional fallback (" + t + ")");
            } catch (Throwable t2) {
                ser = null;
                Bukkit.getLogger().warning("[OreoHolograms] ProtocolLib interceptor: failed to resolve chat component serializer (" + t2 + ")");
            }
        }
        COMPONENT_SERIALIZER = ser;
    }

    /** entity integer ID → raw lines supplier (lines may contain %placeholders%) */
    private final Map<Integer, Supplier<List<String>>> tracked = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public ProtocolLibHoloInterceptor(Plugin plugin) {
        this.plugin = plugin;

        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        onMetadataPacket(event);
                    }
                });

        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.SPAWN_ENTITY) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        onSpawnEntity(event);
                    }
                });

        // Paper 1.21+ bundles SPAWN_ENTITY + ENTITY_METADATA into a ClientboundBundlePacket.
        // ProtocolLib 5.4.0 on 1.21.11 (untested) may not fire individual sub-packet events,
        // so we intercept the BUNDLE directly and replace ENTITY_METADATA inside it.
        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(
                    new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.BUNDLE) {
                        @Override
                        public void onPacketSending(PacketEvent event) {
                            onBundlePacket(event);
                        }
                    });
            Bukkit.getLogger().info("[OreoHolograms] ProtocolLib PAPI interceptor registered (ENTITY_METADATA + SPAWN_ENTITY + BUNDLE).");
        } catch (Throwable t) {
            Bukkit.getLogger().info("[OreoHolograms] ProtocolLib PAPI interceptor registered (ENTITY_METADATA + SPAWN_ENTITY). BUNDLE unavailable: " + t);
        }
    }

    /** Start intercepting metadata packets for this entity. */
    public void track(int entityIntId, Supplier<List<String>> linesSup) {
        tracked.put(entityIntId, linesSup);
    }

    /** Stop intercepting for this entity. */
    public void untrack(int entityIntId) {
        tracked.remove(entityIntId);
    }

    public void clearAll() {
        tracked.clear();
    }

    /**
     * Directly push a per-player ENTITY_METADATA packet using ProtocolLib's API.
     */
    public void pushToPlayer(Player player, int entityId, List<String> lines) {
        if (player == null || !player.isOnline()) return;
        if (COMPONENT_SERIALIZER == null) return;

        try {
            Component resolved = HoloText.render(lines, player);
            String json = GsonComponentSerializer.gson().serialize(resolved);
            Object nmsComp = WrappedChatComponent.fromJson(json).getHandle();

            // TextDisplay.DATA_TEXT_ID is Optional<Component> in MC 1.20+.
            // Wrap the NMS component in Optional so the client accepts the metadata update.
            Object value = java.util.Optional.of(nmsComp);

            WrappedDataValue dv = new WrappedDataValue(TEXT_META_INDEX, COMPONENT_SERIALIZER, value);

            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entityId);
            packet.getDataValueCollectionModifier().write(0, List.of(dv));

            // Pass false to skip listener pipeline — avoids re-intercepting our own packet.
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[OreoHolograms] pushToPlayer failed for "
                    + player.getName() + " entity=" + entityId + ": " + t);
        }
    }

    /**
     * Intercepts ClientboundBundlePacket (Paper 1.21+).
     * When a tracked TextDisplay entity spawns, its ENTITY_METADATA sub-packet inside
     * the bundle contains the server-side (unresolved) text.  We replace it in-place
     * so the client receives per-player resolved text without a separate follow-up packet.
     *
     * This is the primary fix for ProtocolLib 5.4.0 on MC 1.21.11 where individual
     * sub-packet listeners (ENTITY_METADATA, SPAWN_ENTITY) may not fire for bundled packets.
     */
    private void onBundlePacket(PacketEvent event) {
        // This log fires outside the try/catch so we can tell if the listener itself fires
        Bukkit.getLogger().info("[OreoHolograms][DBG] BUNDLE packet received for " + event.getPlayer().getName());
        try {
            var bundleMod = event.getPacket().getPacketBundles();
            Bukkit.getLogger().info("[OreoHolograms][DBG] bundleMod=" + bundleMod);
            Iterable<PacketContainer> subPackets = bundleMod.read(0);
            Bukkit.getLogger().info("[OreoHolograms][DBG] subPackets=" + subPackets);
            if (subPackets == null) return;
            Player player = event.getPlayer();

            // Collect tracked entity IDs whose SPAWN_ENTITY appeared without ENTITY_METADATA,
            // so we can inject an ENTITY_METADATA sub-packet for them.
            java.util.Set<Integer> spawnedTrackedIds = new java.util.LinkedHashSet<>();

            for (PacketContainer inner : subPackets) {
                try {
                    if (inner.getType() == PacketType.Play.Server.SPAWN_ENTITY) {
                        int spawnId = inner.getIntegers().read(0);
                        if (tracked.containsKey(spawnId)) {
                            spawnedTrackedIds.add(spawnId);
                        }
                        continue;
                    }
                    if (inner.getType() != PacketType.Play.Server.ENTITY_METADATA) continue;
                    int entityId = inner.getIntegers().read(0);
                    Supplier<List<String>> linesSup = tracked.get(entityId);
                    if (linesSup == null) continue;

                    // Entity was spawned+metadata in same bundle — no need to inject separately
                    spawnedTrackedIds.remove(entityId);

                    List<String> lines = safeLines(linesSup);
                    Component resolved = HoloText.render(lines, player);
                    String json = GsonComponentSerializer.gson().serialize(resolved);

                    List<WrappedDataValue> values = inner.getDataValueCollectionModifier().read(0);
                    List<WrappedDataValue> modified = (values != null) ? new ArrayList<>(values) : new ArrayList<>();
                    boolean changed = false;

                    for (int i = 0; i < modified.size(); i++) {
                        WrappedDataValue dv = modified.get(i);
                        if (dv.getIndex() != TEXT_META_INDEX) continue;
                        Object nmsComp = WrappedChatComponent.fromJson(json).getHandle();
                        Object newValue = java.util.Optional.of(nmsComp);
                        boolean set = false;
                        try {
                            Method setValue = dv.getClass().getMethod("setValue", Object.class);
                            setValue.invoke(dv, newValue);
                            set = true;
                        } catch (Throwable ignored) {}
                        if (!set) modified.set(i, new WrappedDataValue(dv.getIndex(), dv.getSerializer(), newValue));
                        changed = true;
                        break;
                    }

                    if (!changed && COMPONENT_SERIALIZER != null) {
                        Object nmsComp = WrappedChatComponent.fromJson(json).getHandle();
                        modified.add(new WrappedDataValue(TEXT_META_INDEX, COMPONENT_SERIALIZER, java.util.Optional.of(nmsComp)));
                        changed = true;
                    }

                    if (changed) {
                        inner.getDataValueCollectionModifier().write(0, modified);
                        Bukkit.getLogger().info("[OreoHolograms][DBG] bundle: replaced metadata text for entity=" + entityId + " player=" + player.getName());
                    }
                } catch (Throwable ignored) {}
            }

            // For tracked entities spawned in this bundle without ENTITY_METADATA,
            // schedule an immediate push so the client receives resolved text right away.
            if (!spawnedTrackedIds.isEmpty() && COMPONENT_SERIALIZER != null) {
                for (int spawnedId : spawnedTrackedIds) {
                    Supplier<List<String>> linesSup = tracked.get(spawnedId);
                    if (linesSup == null) continue;
                    List<String> lines = safeLines(linesSup);
                    Bukkit.getLogger().info("[OreoHolograms][DBG] bundle: scheduling post-spawn push for entity=" + spawnedId + " player=" + player.getName());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) pushToPlayer(player, spawnedId, lines);
                    });
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[OreoHolograms][DBG] BUNDLE processing error: " + t);
        }
    }

    private void onSpawnEntity(PacketEvent event) {
        int entityId;
        try {
            entityId = event.getPacket().getIntegers().read(0);
        } catch (Throwable t) {
            return;
        }

        Supplier<List<String>> linesSup = tracked.get(entityId);
        if (linesSup == null) return;

        Player player = event.getPlayer();
        List<String> lines = safeLines(linesSup);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                pushToPlayer(player, entityId, lines);
            }
        });
    }

    private void onMetadataPacket(PacketEvent event) {
        if (TEXT_META_INDEX < 0) return;

        int entityId;
        try {
            entityId = event.getPacket().getIntegers().read(0);
        } catch (Throwable t) {
            return;
        }

        Supplier<List<String>> linesSup = tracked.get(entityId);
        if (linesSup == null) return;

        Player player = event.getPlayer();
        Bukkit.getLogger().info("[OreoHolograms][DBG] ENTITY_METADATA fired for tracked entity=" + entityId
                + " player=" + player.getName() + " TEXT_META_INDEX=" + TEXT_META_INDEX);

        List<String> lines = safeLines(linesSup);
        Component resolved = HoloText.render(lines, player);
        String json = GsonComponentSerializer.gson().serialize(resolved);

        try {
            List<WrappedDataValue> values = event.getPacket().getDataValueCollectionModifier().read(0);
            if (values == null || values.isEmpty()) {
                Bukkit.getLogger().warning("[OreoHolograms][DBG] metadata packet for entity " + entityId + " has null/empty values");
                return;
            }

            // Log all indices present so we can verify TEXT_META_INDEX is correct
            StringBuilder idxLog = new StringBuilder("[OreoHolograms][DBG] entity=").append(entityId)
                    .append(" packet indices: ");
            for (WrappedDataValue v : values) idxLog.append(v.getIndex()).append(' ');
            Bukkit.getLogger().info(idxLog.toString());

            List<WrappedDataValue> modified = new ArrayList<>(values);
            boolean changed = false;

            for (int i = 0; i < modified.size(); i++) {
                WrappedDataValue dv = modified.get(i);
                if (dv.getIndex() != TEXT_META_INDEX) continue;

                Object nmsComp = WrappedChatComponent.fromJson(json).getHandle();
                // Preserve Optional wrapping: TextDisplay.DATA_TEXT_ID is Optional<Component> in MC 1.20+.
                // The server's packet carries Optional<IChatBaseComponent>; our replacement must match.
                Object newValue = java.util.Optional.of(nmsComp);

                boolean set = false;
                try {
                    Method setValue = dv.getClass().getMethod("setValue", Object.class);
                    setValue.invoke(dv, newValue);
                    set = true;
                } catch (Throwable ignored) {
                }

                if (!set) {
                    modified.set(i, new WrappedDataValue(dv.getIndex(), dv.getSerializer(), newValue));
                }
                changed = true;
                break;
            }

            if (changed) {
                event.getPacket().getDataValueCollectionModifier().write(0, modified);
                Bukkit.getLogger().info("[OreoHolograms][DBG] successfully replaced text for entity=" + entityId);
            } else {
                Bukkit.getLogger().warning("[OreoHolograms][DBG] TEXT_META_INDEX=" + TEXT_META_INDEX
                        + " NOT found in packet for entity=" + entityId + " — wrong index?");
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[OreoHolograms] ProtocolLib intercept failed for entity "
                    + entityId + ": " + t);
        }
    }

    private static List<String> safeLines(Supplier<List<String>> sup) {
        try {
            List<String> v = sup.get();
            return v != null ? v : List.of();
        } catch (Throwable t) {
            return List.of();
        }
    }
}