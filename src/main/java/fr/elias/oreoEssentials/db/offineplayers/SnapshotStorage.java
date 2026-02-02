package fr.elias.oreoEssentials.db.offineplayers;

import fr.elias.oreoEssentials.modules.enderchest.EnderSnapshot;

import javax.annotation.Nullable;
import java.util.UUID;

public interface SnapshotStorage {
    @Nullable
    InvSnapshot   loadInv(UUID uuid);
    void                    saveInv(UUID uuid, InvSnapshot snap);
    @Nullable
    EnderSnapshot loadEnder(UUID uuid);
    void                    saveEnder(UUID uuid, EnderSnapshot snap);

    @Nullable InvSnapshot   loadPendingInv(UUID uuid);
    void                    savePendingInv(UUID uuid, InvSnapshot snap);
    void                    clearPendingInv(UUID uuid);

    @Nullable EnderSnapshot loadPendingEnder(UUID uuid);
    void                    savePendingEnder(UUID uuid, EnderSnapshot snap);
    void                    clearPendingEnder(UUID uuid);
}

