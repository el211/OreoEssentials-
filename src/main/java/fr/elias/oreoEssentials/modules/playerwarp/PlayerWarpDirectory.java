package fr.elias.oreoEssentials.modules.playerwarp;

public interface PlayerWarpDirectory {

    void setWarpServer(String warpId, String server);
    String getWarpServer(String warpId);
    void deleteWarp(String warpId);

    default String getWarpPermission(String warpId) { return null; }
    default void setWarpPermission(String warpId, String permission) { }

    default void clearWarpPermission(String warpId) {
        setWarpPermission(warpId, null);
    }
}
