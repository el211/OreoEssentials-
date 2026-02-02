package fr.elias.oreoEssentials.modules.warps.rabbit;

public interface WarpDirectory {

    void setWarpServer(String warpName, String server);
    String getWarpServer(String warpName);
    void deleteWarp(String warpName);

    default String getWarpPermission(String warpName) { return null; }

    default void setWarpPermission(String warpName, String permission) { /* no-op by default */ }

    //  convenience helper
    default void clearWarpPermission(String warpName) {
        setWarpPermission(warpName, null);
    }


}
