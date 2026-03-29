package fr.elias.oreoEssentials.modules.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Packet-based per-player scoreboard sidebar for Folia.
 *
 * Folia's Bukkit Scoreboard API (getNewScoreboard, registerNewObjective,
 * registerNewTeam …) throws UnsupportedOperationException unconditionally.
 * This class bypasses the Bukkit API by constructing NMS objects via reflection
 * and sending packets via ServerPlayer.connection.send(), which IS thread-safe.
 *
 * All NMS access is done through reflection so this compiles against paper-api only.
 * Technique derived from TAB plugin PaperPacketScoreboard (1.21.11).
 */
public final class FoliaScoreboard {

    private static final Logger LOG = Logger.getLogger("OreoEssentials/FoliaScoreboard");
    public static final boolean AVAILABLE;

    // net.minecraft.world.scores.Scoreboard — shared dummy, never registered with any player
    public static final Object DUMMY_SCOREBOARD;

    private static final Object OBJ_CRITERIA_DUMMY;   // ObjectiveCriteria.DUMMY
    private static final Object RENDER_TYPE_INTEGER;  // ObjectiveCriteria.RenderType.INTEGER
    private static final Object DISPLAY_SLOT_SIDEBAR; // DisplaySlot.SIDEBAR

    // Constructors
    private static final Constructor<?> CTOR_OBJECTIVE;
    private static final boolean CTOR_OBJ_HAS_NUMBER_FORMAT; // 7-arg vs 6-arg
    private static final Constructor<?> CTOR_SET_OBJ_PKT;    // (Objective, int)
    private static final Constructor<?> CTOR_SET_DISPLAY_PKT; // (DisplaySlot, Objective)
    private static final Constructor<?> CTOR_SET_SCORE_PKT;  // (String,String,int,Optional,Optional)
    private static final Constructor<?> CTOR_RESET_SCORE_PKT; // (String, String)

    // PaperAdventure.asVanilla(Component) — cached for performance
    private static final Method METHOD_AS_VANILLA;

    static {
        Object dummySb = null, critDummy = null, renderInt = null, dispSidebar = null;
        Constructor<?> ctorObj = null, ctorSetObj = null, ctorSetDisp = null,
                ctorSetScore = null, ctorReset = null;
        boolean hasNF = false;
        Method asVanilla = null;
        boolean ok = false;
        try {
            // ── Scoreboard (dummy) ────────────────────────────────────────────
            Class<?> cSb = Class.forName("net.minecraft.world.scores.Scoreboard");
            dummySb = cSb.getDeclaredConstructor().newInstance();

            // ── ObjectiveCriteria ─────────────────────────────────────────────
            Class<?> cCrit = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria");
            critDummy = cCrit.getField("DUMMY").get(null);

            Class<?> cRenderType = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType");
            renderInt = cRenderType.getField("INTEGER").get(null);

            // ── DisplaySlot ───────────────────────────────────────────────────
            Class<?> cDisplaySlot = Class.forName("net.minecraft.world.scores.DisplaySlot");
            dispSidebar = cDisplaySlot.getField("SIDEBAR").get(null);

            // ── NMS Component ─────────────────────────────────────────────────
            Class<?> cNmsComp = Class.forName("net.minecraft.network.chat.Component");

            // ── PaperAdventure.asVanilla ──────────────────────────────────────
            Class<?> cPA = Class.forName("io.papermc.paper.adventure.PaperAdventure");
            asVanilla = cPA.getDeclaredMethod("asVanilla", Component.class);
            asVanilla.setAccessible(true);

            // ── Objective constructor (7-arg in 1.20.5+, 6-arg in older) ─────
            Class<?> cObj = Class.forName("net.minecraft.world.scores.Objective");
            Class<?> cNF = null;
            try { cNF = Class.forName("net.minecraft.network.chat.numbers.NumberFormat"); } catch (Throwable ignored) {}

            if (cNF != null) {
                try {
                    ctorObj = cObj.getDeclaredConstructor(cSb, String.class, cCrit, cNmsComp, cRenderType, boolean.class, cNF);
                    hasNF = true;
                } catch (NoSuchMethodException ignored) {}
            }
            if (ctorObj == null) {
                ctorObj = cObj.getDeclaredConstructor(cSb, String.class, cCrit, cNmsComp, cRenderType, boolean.class);
            }
            ctorObj.setAccessible(true);

            // ── Packet constructors ───────────────────────────────────────────
            Class<?> cSetObjPkt = Class.forName("net.minecraft.network.protocol.game.ClientboundSetObjectivePacket");
            ctorSetObj = cSetObjPkt.getDeclaredConstructor(cObj, int.class);
            ctorSetObj.setAccessible(true);

            Class<?> cSetDispPkt = Class.forName("net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket");
            ctorSetDisp = cSetDispPkt.getDeclaredConstructor(cDisplaySlot, cObj);
            ctorSetDisp.setAccessible(true);

            Class<?> cSetScorePkt = Class.forName("net.minecraft.network.protocol.game.ClientboundSetScorePacket");
            ctorSetScore = cSetScorePkt.getDeclaredConstructor(String.class, String.class, int.class, Optional.class, Optional.class);
            ctorSetScore.setAccessible(true);

            Class<?> cResetScorePkt = Class.forName("net.minecraft.network.protocol.game.ClientboundResetScorePacket");
            ctorReset = cResetScorePkt.getDeclaredConstructor(String.class, String.class);
            ctorReset.setAccessible(true);

            ok = true;
        } catch (Throwable t) {
            LOG.severe("[FoliaScoreboard] Static init failed — scoreboard will be unavailable on Folia: " + t);
        }
        DUMMY_SCOREBOARD           = dummySb;
        OBJ_CRITERIA_DUMMY         = critDummy;
        RENDER_TYPE_INTEGER        = renderInt;
        DISPLAY_SLOT_SIDEBAR       = dispSidebar;
        CTOR_OBJECTIVE             = ctorObj;
        CTOR_OBJ_HAS_NUMBER_FORMAT = hasNF;
        CTOR_SET_OBJ_PKT           = ctorSetObj;
        CTOR_SET_DISPLAY_PKT       = ctorSetDisp;
        CTOR_SET_SCORE_PKT         = ctorSetScore;
        CTOR_RESET_SCORE_PKT       = ctorReset;
        METHOD_AS_VANILLA          = asVanilla;
        AVAILABLE                  = ok;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static final String OBJ_NAME      = "oreo_sb";
    private static final int    METHOD_ADD    = 0;
    private static final int    METHOD_REMOVE = 1;
    private static final int    METHOD_CHANGE = 2;

    private final Player player;
    private Object nmsObjective; // net.minecraft.world.scores.Objective, or null if hidden
    private final List<String> activeHolders = new ArrayList<>();
    /** How many timer refreshes have happened since CREATE was last sent. */
    private int refreshCount = 0;
    /** Re-send CREATE+DISPLAY every this many refresh calls to recover from missed initial packet. */
    private static final int RESYNC_INTERVAL = 20; // ~10 s at 10-tick timer

    FoliaScoreboard(Player player) {
        this.player = player;
    }

    boolean isActive() { return nmsObjective != null; }

    /**
     * Creates (or fully refreshes if already shown) the sidebar for this player.
     *
     * @param title Adventure Component for the sidebar header
     * @param lines One Component per visible line, top → bottom order
     */
    void show(Component title, List<Component> lines) {
        if (!AVAILABLE) {
            LOG.warning("[FoliaScoreboard] show() called but AVAILABLE=false for " + player.getName());
            return;
        }

        refreshCount++;
        // Force a full re-create periodically to recover from missed initial packet.
        // Also always force on the very first call (refreshCount==1) and on RESYNC_INTERVAL.
        boolean forceRecreate = (refreshCount == 1) || (refreshCount % RESYNC_INTERVAL == 0);

        if (isActive() && !forceRecreate) {
            // Normal update path: just change title and refresh lines
            try {
                Object newObj = buildObjective(title);
                nmsObjective = newObj;
                sendPkt(CTOR_SET_OBJ_PKT.newInstance(nmsObjective, METHOD_CHANGE));
                sendLines(lines);
            } catch (Throwable t) {
                LOG.warning("[FoliaScoreboard] show(update) failed for " + player.getName() + ": " + t);
            }
            return;
        }

        // Full re-create path (first show, or periodic resync)
        try {
            // Remove old objective first so the client resets its state
            if (nmsObjective != null) {
                try { sendPkt(CTOR_SET_OBJ_PKT.newInstance(nmsObjective, METHOD_REMOVE)); } catch (Throwable ignored) {}
            }
            nmsObjective = buildObjective(title);
            sendPkt(CTOR_SET_OBJ_PKT.newInstance(nmsObjective, METHOD_ADD));
            sendPkt(CTOR_SET_DISPLAY_PKT.newInstance(DISPLAY_SLOT_SIDEBAR, nmsObjective));
            activeHolders.clear(); // force full line resend
            sendLines(lines);
            LOG.info("[FoliaScoreboard] Scoreboard (re)created for " + player.getName()
                    + " (" + lines.size() + " lines, refresh#" + refreshCount + ")");
        } catch (Throwable t) {
            LOG.warning("[FoliaScoreboard] show(create) failed for " + player.getName() + ": " + t);
            nmsObjective = null;
        }
    }

    /** Removes the sidebar from this player's client. */
    void hide() {
        if (nmsObjective == null) return;
        try {
            sendPkt(CTOR_SET_OBJ_PKT.newInstance(nmsObjective, METHOD_REMOVE));
        } catch (Throwable ignored) {}
        activeHolders.clear();
        nmsObjective = null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Object buildObjective(Component title) throws Throwable {
        Object nmsTitle = toNms(title);
        if (CTOR_OBJ_HAS_NUMBER_FORMAT) {
            return CTOR_OBJECTIVE.newInstance(DUMMY_SCOREBOARD, OBJ_NAME,
                    OBJ_CRITERIA_DUMMY, nmsTitle, RENDER_TYPE_INTEGER, false, null);
        } else {
            return CTOR_OBJECTIVE.newInstance(DUMMY_SCOREBOARD, OBJ_NAME,
                    OBJ_CRITERIA_DUMMY, nmsTitle, RENDER_TYPE_INTEGER, false);
        }
    }

    private void sendLines(List<Component> lines) throws Throwable {
        for (String h : activeHolders) {
            sendPkt(CTOR_RESET_SCORE_PKT.newInstance(h, OBJ_NAME));
        }
        activeHolders.clear();

        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String holder = "\u00a7" + Integer.toHexString(i % 16);
            activeHolders.add(holder);
            Object nmsComp = toNms(lines.get(i));
            sendPkt(CTOR_SET_SCORE_PKT.newInstance(
                    holder, OBJ_NAME, score--, Optional.of(nmsComp), Optional.empty()));
        }
    }

    public static Object toNms(Component component) throws Throwable {
        return METHOD_AS_VANILLA.invoke(null, component);
    }

    void sendPkt(Object packet) throws Throwable {
        sendPacketToPlayer(player, packet);
    }

    public static void sendPacketToPlayer(Player player, Object packet) throws Throwable {
        // Direct Netty channel write — thread-safe from any region thread on Folia.
        // ServerPlayer.connection (ServerGamePacketListenerImpl)
        //   → ServerCommonPacketListenerImpl.connection (Connection)
        //   → Connection.channel (io.netty.channel.Channel)
        //   → channel.writeAndFlush(packet)
        Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
        Field listenerField = findField(nmsPlayer.getClass(), "connection");
        listenerField.setAccessible(true);
        Object listener = listenerField.get(nmsPlayer);
        Field connField = findField(listener.getClass(), "connection");
        connField.setAccessible(true);
        Object netConn = connField.get(listener);
        Field channelField = findField(netConn.getClass(), "channel");
        channelField.setAccessible(true);
        Object channel = channelField.get(netConn);
        // ProtocolLib wraps the real Netty channel in NettyChannelProxy (package-private class).
        // Calling getMethod().invoke() on a package-private class throws IllegalAccessException
        // even for public methods. Unwrap to the real delegate channel if present.
        try {
            Field delegateField = findField(channel.getClass(), "delegate");
            delegateField.setAccessible(true);
            channel = delegateField.get(channel);
        } catch (NoSuchFieldException ignored) {}
        // io.netty.channel.Channel.writeAndFlush(Object) — called reflectively
        // so Netty doesn't need to be on the compile classpath.
        Method waf = channel.getClass().getMethod("writeAndFlush", Object.class);
        waf.setAccessible(true);
        waf.invoke(channel, packet);
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " not found in " + cls.getName());
    }

}
