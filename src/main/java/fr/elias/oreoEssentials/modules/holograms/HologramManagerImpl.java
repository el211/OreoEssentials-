package fr.elias.oreoEssentials.modules.holograms;

import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import fr.elias.oreoEssentials.modules.holograms.api.HologramManager;
import fr.elias.oreoEssentials.modules.holograms.api.data.DisplayHologramData;
import fr.elias.oreoEssentials.modules.holograms.api.data.HologramData;
import fr.elias.oreoEssentials.modules.holograms.api.data.TextHologramData;
import fr.elias.oreoEssentials.modules.holograms.api.events.HologramsLoadedEvent;
import fr.elias.oreoEssentials.modules.holograms.api.events.HologramsUnloadedEvent;
import fr.elias.oreoEssentials.modules.holograms.api.hologram.Hologram;
import fr.elias.oreoEssentials.util.OreScheduler;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The OHologramsManager class is responsible for managing holograms in the OHolograms plugin.
 * It provides methods for adding, removing, and retrieving holograms, as well as other related operations.
 */
public final class HologramManagerImpl implements HologramManager {

    private final @NotNull OHolograms plugin;
    /**
     * The adapter function used to create holograms from hologram data.
     */
    private final @NotNull Function<HologramData, Hologram> adapter;
    /**
     * A map of hologram names to their corresponding hologram instances.
     */
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    /**
     * Whether holograms are loaded or not
     */
    private boolean isLoaded = false;

    HologramManagerImpl(@NotNull final OHolograms plugin, @NotNull final Function<HologramData, Hologram> adapter) {
        this.plugin = plugin;
        this.adapter = adapter;
        this.hologramLoadLogging = plugin.getHologramConfiguration().isHologramLoadLogging();
    }

    /**
     * Whether hologram loading should be logged on world loading.
     */
    private final boolean hologramLoadLogging;


    /**
     * @return A read-only collection of loaded holograms.
     */
    @Override
    public @NotNull
    @UnmodifiableView Collection<Hologram> getHolograms() {
        return Collections.unmodifiableCollection(this.holograms.values());
    }

    /**
     * Returns a read-only view of the currently loaded persistent holograms.
     *
     * @return A read-only collection of holograms.
     */
    @Override
    public @NotNull
    @UnmodifiableView Collection<Hologram> getPersistentHolograms() {
        return this.holograms.values().stream().filter(hologram -> hologram.getData().isPersistent()).toList();
    }


    /**
     * Finds a hologram by name.
     *
     * @param name The name of the hologram to lookup.
     * @return An optional containing the found hologram, or empty if not found.
     */
    public @NotNull Optional<Hologram> getHologram(@NotNull final String name) {
        return Optional.ofNullable(this.holograms.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Adds a hologram to this manager.
     *
     * @param hologram The hologram to add.
     */
    public void addHologram(@NotNull final Hologram hologram) {
        this.holograms.put(hologram.getData().getName().toLowerCase(Locale.ROOT), hologram);
    }

    /**
     * Removes a hologram from this manager.
     *
     * @param hologram The hologram to remove.
     */
    public void removeHologram(@NotNull final Hologram hologram) {
        this.removeHologram(hologram.getData().getName());
    }

    /**
     * Removes a hologram from this manager by name.
     *
     * @param name The name of the hologram to remove.
     * @return An optional containing the removed hologram, or empty if not found.
     */
    public @NotNull Optional<Hologram> removeHologram(@NotNull final String name) {
        Optional<Hologram> optionalHologram = Optional.ofNullable(this.holograms.remove(name.toLowerCase(Locale.ROOT)));

        optionalHologram.ifPresent(hologram -> {
                    for (UUID viewer : hologram.getViewers()) {
                        Player player = Bukkit.getPlayer(viewer);
                        if (player != null) {
                            OreScheduler.runForEntity(plugin.getPlugin(), player, () -> hologram.forceHideHologram(player));
                        }
                    }

                    OreScheduler.runAtLocation(plugin.getPlugin(), hologram.getData().getLocation(), hologram::deleteHologram);

                    OHolograms.get().getHologramThread().submit(() -> plugin.getHologramStorage().delete(hologram));
                }
        );

        return optionalHologram;
    }

    /**
     * Creates a new hologram with the specified hologram data.
     *
     * @param data The hologram data for the new hologram.
     * @return The created hologram.
     */
    public @NotNull Hologram create(@NotNull final HologramData data) {
        return this.adapter.apply(data);
    }

    public void saveHolograms() {
        if (!isLoaded) {
            return;
        }

        plugin.getHologramStorage().saveBatch(getPersistentHolograms(), false);
    }

    @Override
    public void loadHolograms() {
        List<Hologram> allLoaded = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            Collection<Hologram> loaded = plugin.getHologramStorage().loadAll(world.getName());
            loaded.forEach(this::addHologram);

            allLoaded.addAll(loaded);
        }
        this.isLoaded = true;

        OreScheduler.run(plugin.getPlugin(), () -> {
            Bukkit.getPluginManager().callEvent(new HologramsLoadedEvent(ImmutableList.copyOf(allLoaded)));
            for (Hologram hologram : allLoaded) {
                if (hologram.getData().getLinkedNpcName() != null) {
                    syncHologramWithNpc(hologram);
                }
            }
        });

        if (hologramLoadLogging) OHolograms.get().getFancyLogger().info(String.format("Loaded %d holograms for all loaded worlds", allLoaded.size()));
    }

    @Override
    public boolean isLoaded() {
        return this.isLoaded;
    }

    public void loadHolograms(String world) {
        ImmutableList<Hologram> loaded = ImmutableList.copyOf(this.plugin.getHologramStorage().loadAll(world));
        loaded.forEach(this::addHologram);

        this.isLoaded = true;

        OreScheduler.run(plugin.getPlugin(), () -> {
            Bukkit.getPluginManager().callEvent(new HologramsLoadedEvent(ImmutableList.copyOf(loaded)));
            for (Hologram hologram : loaded) {
                if (hologram.getData().getLinkedNpcName() != null) {
                    syncHologramWithNpc(hologram);
                }
            }
        });

        if (this.hologramLoadLogging)
            OHolograms.get().getFancyLogger().info(String.format("Loaded %d holograms for world %s", loaded.size(), world));
    }

    /**
     * Initializes tasks for managing holograms, such as loading and refreshing them.
     * <p>
     * This method is intended to be called internally by the plugin.
     */
    void initializeTasks() {
        ScheduledExecutorService hologramThread = this.plugin.getHologramThread();
        OreScheduler.run(plugin.getPlugin(), this::loadHolograms);

        hologramThread.scheduleAtFixedRate(() -> {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                OreScheduler.runForEntity(plugin.getPlugin(), player, () -> {
                    for (final Hologram hologram : this.plugin.getHologramsManager().getHolograms()) {
                        hologram.forceUpdateShownStateFor(player);
                    }
                });
            }
        }, 0, this.plugin.getHologramConfiguration().getUpdateVisibilityInterval() * 50L, TimeUnit.MILLISECONDS);

        final var updateTimes = CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(5))
                .<String, Long>build();

        hologramThread.scheduleAtFixedRate(() -> {
            final var time = System.currentTimeMillis();

            for (final var hologram : this.getHolograms()) {
                HologramData data = hologram.getData();
                if (data.hasChanges()) {
                    OreScheduler.runAtLocation(plugin.getPlugin(), data.getLocation(), () -> {
                        hologram.forceUpdate();
                        for (UUID viewer : hologram.getViewers()) {
                            Player player = Bukkit.getPlayer(viewer);
                            if (player != null && player.getWorld().equals(data.getLocation().getWorld())) {
                                OreScheduler.runForEntity(plugin.getPlugin(), player, () -> hologram.refreshHologram(player));
                            }
                        }
                        data.setHasChanges(false);
                    });

                    if (data instanceof TextHologramData) {
                        updateTimes.put(hologram.getData().getName(), time);
                    }
                }
            }
        }, 50, 1000, TimeUnit.MILLISECONDS);

        hologramThread.scheduleWithFixedDelay(() -> {
            final var time = System.currentTimeMillis();

            for (final var hologram : this.getHolograms()) {
                if (hologram.getData() instanceof TextHologramData textData) {
                    final var interval = textData.getTextUpdateInterval();
                    if (interval < 1) {
                        continue;
                    }

                    final var lastUpdate = updateTimes.asMap().get(textData.getName());
                    if (lastUpdate != null && time < (lastUpdate + interval)) {
                        continue;
                    }

                    if (lastUpdate == null || time > (lastUpdate + interval)) {
                        for (UUID viewer : hologram.getViewers()) {
                            Player player = Bukkit.getPlayer(viewer);
                            if (player != null && player.getWorld().equals(textData.getLocation().getWorld())) {
                                OreScheduler.runForEntity(plugin.getPlugin(), player, () -> hologram.refreshHologram(player));
                            }
                        }
                        updateTimes.put(textData.getName(), time);
                    }
                }
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Reloads holograms by clearing the existing holograms and loading them again from the plugin's configuration.
     */
    public void reloadHolograms() {
        this.unloadHolograms();
        this.loadHolograms();
    }

    public void unloadHolograms() {
        OHolograms.get().getHologramThread().submit(() -> {
            List<Hologram> unloaded = new ArrayList<>();

            for (final var hologram : this.getPersistentHolograms()) {
                this.holograms.remove(hologram.getName());
                unloaded.add(hologram);

                for (UUID viewer : hologram.getViewers()) {
                    Player player = Bukkit.getPlayer(viewer);
                    if (player != null) {
                        OreScheduler.runForEntity(plugin.getPlugin(), player, () -> hologram.forceHideHologram(player));
                    }
                }

                OreScheduler.runAtLocation(plugin.getPlugin(), hologram.getData().getLocation(), hologram::deleteHologram);
            }

            OreScheduler.run(plugin.getPlugin(), () -> Bukkit.getPluginManager().callEvent(new HologramsUnloadedEvent(ImmutableList.copyOf(unloaded))));
        });
    }

    public void unloadHolograms(String world) {
        final var online = List.copyOf(Bukkit.getOnlinePlayers());

        OHolograms.get().getHologramThread().submit(() -> {
            List<Hologram> h = this.getPersistentHolograms().stream()
                    .filter(hologram -> hologram.getData().getLocation().getWorld().getName().equals(world))
                    .toList();

            OHolograms.get().getHologramStorage().saveBatch(h, false);

            for (final Hologram hologram : h) {
                this.holograms.remove(hologram.getName());
                online.forEach(player -> OreScheduler.runForEntity(plugin.getPlugin(), player, () -> hologram.forceHideHologram(player)));
                OreScheduler.runAtLocation(plugin.getPlugin(), hologram.getData().getLocation(), hologram::deleteHologram);
            }

            OreScheduler.run(plugin.getPlugin(), () -> Bukkit.getPluginManager().callEvent(new HologramsUnloadedEvent(ImmutableList.copyOf(h))));
        });
    }

    /**
     * Syncs a hologram with its linked NPC, if any.
     *
     * @param hologram The hologram to sync.
     */
    public void syncHologramWithNpc(@NotNull final Hologram hologram) {
        final var linkedNpcName = hologram.getData().getLinkedNpcName();
        if (linkedNpcName == null) {
            return;
        }

        final var npc = FancyNpcsPlugin.get().getNpcManager().getNpc(linkedNpcName);
        if (npc == null) {
            return;
        }

        npc.getData().setDisplayName("<empty>");
        npc.getData().setShowInTab(false);
        npc.updateForAll();

        final var npcScale = npc.getData().getScale();

        if (hologram.getData() instanceof DisplayHologramData displayData) {
            displayData.setScale(new Vector3f(npcScale));
        }

        final var location = npc.getData().getLocation().clone().add(0, (npc.getEyeHeight() * npcScale) + (0.5 * npcScale), 0);

        if (npc.getData().getType() == EntityType.PLAYER) {
            final var poseAttr = FancyNpcsPlugin.get().getAttributeManager().getAttributeByName(npc.getData().getType(), "pose");
            if (poseAttr != null) {
                final var pose = npc.getData().getAttributes().get(poseAttr);
                if (pose != null) {
                    switch (pose.toLowerCase()) {
                        case "sitting" -> location.subtract(0, 0.7 * npcScale, 0);
                        case "sleeping" -> location.subtract(0, 0.4 * npcScale, 0);
                        case "crouching" -> location.subtract(0, 0.1 * npcScale, 0);
                    }
                }
            }
        }

        hologram.getData().setLocation(location);
    }
}
