package fr.elias.oreoEssentials.modules.holograms.nms.v1_21_10;

import fr.elias.oreoEssentials.modules.holograms.nms.NmsHologramBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;


public final class Bridge_v1_21_10 implements NmsHologramBridge {

    private static final Logger LOG = Logger.getLogger("OreoEssentials/NMS");

    private static final Object TEXT_ACCESSOR;
    private static final boolean INIT_OK;

    static {
        Object accessor = null;
        boolean ok = false;
        try {
            Class<?> clsTextDisplay = Class.forName("net.minecraft.world.entity.Display$TextDisplay");
            Field f = clsTextDisplay.getDeclaredField("DATA_TEXT_ID");
            f.setAccessible(true);
            accessor = f.get(null);
            ok = true;
        } catch (Throwable t) {
            LOG.severe("[Bridge_v1_21_10] Static init failed: " + t);
        }
        TEXT_ACCESSOR = accessor;
        INIT_OK = ok;
    }

    @Override
    public String craftBukkitVersion() {
        return "v1_21_10";
    }

    @Override
    public void sendTextDisplayText(Player player, int entityId, Component text) {
        if (!INIT_OK) return;
        try {
            Object nmsComponent = adventureToNms(text);

            Class<?> clsDataValue = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            Method create = clsDataValue.getDeclaredMethod("create",
                    Class.forName("net.minecraft.network.syncher.EntityDataAccessor"),
                    Object.class);
            create.setAccessible(true);
            Object dataValue = create.invoke(null, TEXT_ACCESSOR, nmsComponent);

            Class<?> clsPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            Object packet = clsPacket
                    .getDeclaredConstructor(int.class, List.class)
                    .newInstance(entityId, List.of(dataValue));

            sendPacket(player, packet);
        } catch (Throwable t) {
            LOG.warning("[Bridge_v1_21_10] sendTextDisplayText failed: " + t);
        }
    }

    @Override
    public void destroyEntityClientside(Player player, int entityId) {
        try {
            Class<?> clsPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            Object packet = clsPacket
                    .getDeclaredConstructor(int[].class)
                    .newInstance(new int[]{entityId});
            sendPacket(player, packet);
        } catch (Throwable t) {
            LOG.warning("[Bridge_v1_21_10] destroyEntityClientside failed: " + t);
        }
    }

    @Override
    public void teleportEntityClientside(Player player, int entityId,
                                         double x, double y, double z,
                                         float yaw, float pitch, boolean onGround) {
        try {
            Class<?> clsVec3 = Class.forName("net.minecraft.world.phys.Vec3");
            Object pos   = clsVec3.getDeclaredConstructor(double.class, double.class, double.class).newInstance(x, y, z);
            Object delta = clsVec3.getDeclaredField("ZERO").get(null);

            Class<?> clsPMR = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
            Object pmr = clsPMR
                    .getDeclaredConstructor(clsVec3, clsVec3, float.class, float.class)
                    .newInstance(pos, delta, yaw, pitch);

            Class<?> clsPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
            Object packet = clsPacket
                    .getDeclaredConstructor(int.class, clsPMR, java.util.Set.class, boolean.class)
                    .newInstance(entityId, pmr, java.util.Set.of(), onGround);

            sendPacket(player, packet);
        } catch (Throwable t) {
            LOG.warning("[Bridge_v1_21_10] teleportEntityClientside failed: " + t);
        }
    }

    private static Object adventureToNms(Component component) throws Throwable {
        Class<?> paperAdventure = Class.forName("io.papermc.paper.adventure.PaperAdventure");
        Method asVanilla = paperAdventure.getDeclaredMethod("asVanilla", Component.class);
        asVanilla.setAccessible(true);
        return asVanilla.invoke(null, component);
    }

    private static void sendPacket(Player player, Object packet) throws Throwable {
        Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
        Field connField = findField(nmsPlayer.getClass(), "connection");
        connField.setAccessible(true);
        Object connection = connField.get(nmsPlayer);
        Method send = findSendMethod(connection.getClass(), packet.getClass());
        send.invoke(connection, packet);
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " not found in hierarchy of " + cls.getName());
    }

    private static Method findSendMethod(Class<?> cls, Class<?> packetClass) throws NoSuchMethodException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("send")) continue;
                if (m.getParameterCount() != 1)  continue;
                if (m.getParameterTypes()[0].isAssignableFrom(packetClass)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException("send(Packet<?>) not found on " + cls.getName());
    }
}
