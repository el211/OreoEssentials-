package fr.elias.oreoEssentials.modules.holograms.perplayer_nms;

import fr.elias.oreoEssentials.modules.holograms.HoloText;
import fr.elias.oreoEssentials.modules.holograms.nms.NmsHologramBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.*;
import java.util.function.Function;

public final class PerPlayerTextDisplayController {

    private final NmsHologramBridge nms;

    private final Map<UUID, Set<UUID>> viewersByHolo = new HashMap<>();
    private final Map<UUID, Long> lastRefreshTick = new HashMap<>();
    private final Function<UUID, List<String>> lineResolver;

    public PerPlayerTextDisplayController(NmsHologramBridge nms,
                                          Function<UUID, List<String>> lineResolver) {
        this.nms          = Objects.requireNonNull(nms,          "nms");
        this.lineResolver = Objects.requireNonNull(lineResolver, "lineResolver");
    }


    public void tick(Collection<TextDisplay> holos, long refreshEveryTicks) {
        long nowTick = safeTick();

        for (TextDisplay td : holos) {
            if (td == null || td.isDead()) continue;

            UUID holoId = td.getUniqueId();
            long last   = lastRefreshTick.getOrDefault(holoId, 0L);
            if (nowTick - last < refreshEveryTicks) continue;

            lastRefreshTick.put(holoId, nowTick);
            refreshForTrackedViewers(td);
        }
    }


    public void show(TextDisplay td, Player p, List<String> lines) {
        if (td == null || p == null || !p.isOnline()) return;

        viewersByHolo
                .computeIfAbsent(td.getUniqueId(), __ -> new HashSet<>())
                .add(p.getUniqueId());

        refresh(td, p, lines);
    }


    public void hide(TextDisplay td, Player p) {
        if (td == null || p == null) return;

        Set<UUID> viewers = viewersByHolo.get(td.getUniqueId());
        if (viewers != null) viewers.remove(p.getUniqueId());
    }


    public void refresh(TextDisplay td, Player p, List<String> lines) {
        if (td == null || p == null || !p.isOnline()) return;

        Component comp = HoloText.render(lines, p); // player-context render
        nms.sendTextDisplayText(p, td.getEntityId(), comp);
    }


    public boolean isTracked(UUID holoId, UUID playerId) {
        Set<UUID> viewers = viewersByHolo.get(holoId);
        return viewers != null && viewers.contains(playerId);
    }


    public void untrackHologram(UUID holoId) {
        viewersByHolo.remove(holoId);
        lastRefreshTick.remove(holoId);
    }


    private void refreshForTrackedViewers(TextDisplay td) {
        Set<UUID> viewers = viewersByHolo.get(td.getUniqueId());
        if (viewers == null || viewers.isEmpty()) return;

        // Resolve lines once per hologram, then render per player (PAPI applies player context inside render())
        List<String> lines = resolveLinesFor(td);

        for (UUID uuid : new ArrayList<>(viewers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                viewers.remove(uuid); // clean up stale viewer
                continue;
            }
            refresh(td, p, lines);
        }
    }


    private List<String> resolveLinesFor(TextDisplay td) {
        List<String> lines = lineResolver.apply(td.getUniqueId());
        return (lines == null) ? List.of() : lines;
    }

    private long safeTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (Throwable t) {
            return System.currentTimeMillis() / 50L;
        }
    }
}