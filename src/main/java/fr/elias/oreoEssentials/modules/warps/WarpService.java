package fr.elias.oreoEssentials.modules.warps;

import fr.elias.oreoEssentials.modules.warps.rabbit.WarpDirectory;
import fr.elias.oreoEssentials.services.StorageApi;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

public class WarpService {

    private final StorageApi storage;
    private final WarpDirectory directory;

    public WarpService(StorageApi storage) {
        this(storage, null);
    }

    public WarpService(StorageApi storage, WarpDirectory directory) {
        this.storage = storage;
        this.directory = directory;
    }


    public boolean setWarp(String name, Location loc) {
        storage.setWarp(name, loc);
        return true;
    }

    public boolean delWarp(String name) {
        boolean ok = storage.delWarp(name);
        if (ok && directory != null) {
            try { directory.deleteWarp(name); } catch (Throwable ignored) {}
        }
        return ok;
    }

    public Location getWarp(String name) {
        return storage.getWarp(name);
    }

    public Set<String> listWarps() {
        return storage.listWarps();
    }


    public String requiredPermission(String warp) {
        return (directory == null ? null : directory.getWarpPermission(warp));
    }

    public boolean canUse(Player p, String warp) {
        String perm = requiredPermission(warp);
        return (perm == null || perm.isBlank()) || p.hasPermission(perm);
    }

    public String getWarpPermission(String warp) {
        return (directory == null ? null : directory.getWarpPermission(warp));
    }


    public void setWarpPermission(String warp, String permission) {
        if (directory == null) return;
        directory.setWarpPermission(warp, permission);
    }


    public boolean renameWarp(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        oldName = oldName.trim().toLowerCase(Locale.ROOT);
        newName = newName.trim().toLowerCase(Locale.ROOT);
        if (oldName.isEmpty() || newName.isEmpty() || oldName.equals(newName)) return false;

        Location loc = storage.getWarp(oldName);
        if (loc == null) return false;
        if (storage.getWarp(newName) != null) return false;

        storage.setWarp(newName, loc);
        boolean delOk = storage.delWarp(oldName);
        if (!delOk) {
            storage.delWarp(newName);
            return false;
        }

        if (directory != null) {
            try {
                String server = directory.getWarpServer(oldName);
                String perm   = directory.getWarpPermission(oldName);
                if (server != null) directory.setWarpServer(newName, server);
                if (perm != null && !perm.isBlank()) directory.setWarpPermission(newName, perm);
                directory.deleteWarp(oldName);
            } catch (Throwable ignored) {}
        }

        return true;
    }
}
