package fr.elias.oreoEssentials.playersync;

public final class PlayerSyncPrefs {
    public boolean inv;
    public boolean xp;
    public boolean health;
    public boolean hunger;

    public static PlayerSyncPrefs defaults(boolean inv, boolean xp, boolean health, boolean hunger) {
        PlayerSyncPrefs p = new PlayerSyncPrefs();
        p.inv = inv; p.xp = xp; p.health = health; p.hunger = hunger;
        return p;
    }
}