package fr.elias.oreoEssentials.holograms.perplayer_nms;

import fr.elias.oreoEssentials.holograms.HoloText;
import fr.elias.oreoEssentials.holograms.nms.NmsHologramBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.*;

public final class PerPlayerTextDisplayController {

    private final NmsHologramBridge nms;

    // track viewers so you can refresh on interval without spamming everyone
    private final Map<UUID, Set<UUID>> viewersByHolo = new HashMap<>();
    // holo UUID -> last refresh tick
    private final Map<UUID, Long> lastRefreshTick = new HashMap<>();

    public PerPlayerTextDisplayController(NmsHologramBridge nms) {
        this.nms = Objects.requireNonNull(nms, "nms");
    }

    /** Called on a repeating task (ex: every 10 ticks) */
    public void tick(Collection<TextDisplay> holos, long refreshEveryTicks) {
        long nowTick = safeTick();

        for (TextDisplay td : holos) {
            if (td == null || td.isDead()) continue;

            long last = lastRefreshTick.getOrDefault(td.getUniqueId(), 0L);
            if (nowTick - last < refreshEveryTicks) continue;
            lastRefreshTick.put(td.getUniqueId(), nowTick);

            refreshForTrackedViewers(td);
        }
    }

    /** Call this when a player comes into range / should see it. */
    public void show(TextDisplay td, Player p, List<String> lines) {
        if (td == null || p == null) return;
        viewersByHolo.computeIfAbsent(td.getUniqueId(), __ -> new HashSet<>()).add(p.getUniqueId());
        refresh(td, p, lines);
    }

    /** Call this when a player leaves range / quits / hides it. */
    public void hide(TextDisplay td, Player p) {
        if (td == null || p == null) return;
        Set<UUID> set = viewersByHolo.get(td.getUniqueId());
        if (set != null) set.remove(p.getUniqueId());
        // We do NOT destroy the real entity; vanilla tracking handles it.
        // If you had fake clientside entities, you'd destroy here.
    }

    /** Per-player refresh: THIS is the missing piece for %player_name% */
    public void refresh(TextDisplay td, Player p, List<String> lines) {
        if (td == null || p == null || !p.isOnline()) return;

        Component comp = HoloText.render(lines, p); // <-- player context
        nms.sendTextDisplayText(p, td.getEntityId(), comp);
    }

    private void refreshForTrackedViewers(TextDisplay td) {
        Set<UUID> viewers = viewersByHolo.get(td.getUniqueId());
        if (viewers == null || viewers.isEmpty()) return;

        // You must pass the correct lines for this hologram from your own storage.
        // So here you’ll call your own resolver; shown as pseudo:
        List<String> lines = resolveLinesFor(td);

        for (UUID uuid : new ArrayList<>(viewers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                viewers.remove(uuid);
                continue;
            }
            refresh(td, p, lines);
        }
    }

    private long safeTick() {
        try { return Bukkit.getCurrentTick(); }
        catch (Throwable t) { return System.currentTimeMillis() / 50L; }
    }

    private List<String> resolveLinesFor(TextDisplay td) {
        // TODO: connect to your OreoHologramData store.
        // Return lines for this hologram.
        return List.of();
    }
}
