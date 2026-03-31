package fr.elias.oreoEssentials.modules.holograms.api;

import fr.elias.oreoEssentials.modules.holograms.api.data.HologramData;
import fr.elias.oreoEssentials.modules.holograms.api.hologram.Hologram;

import java.util.Collection;
import java.util.Optional;

public interface HologramManager {

    Optional<Hologram> getHologram(String name);

    Collection<Hologram> getPersistentHolograms();

    Collection<Hologram> getHolograms();

    void addHologram(Hologram hologram);

    void removeHologram(Hologram hologram);

    Hologram create(HologramData hologramData);

    void loadHolograms();

    boolean isLoaded();

    void saveHolograms();

    void reloadHolograms();

}
