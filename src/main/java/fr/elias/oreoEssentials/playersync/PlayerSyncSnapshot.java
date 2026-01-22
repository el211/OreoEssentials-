package fr.elias.oreoEssentials.playersync;

import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.Base64;

public final class PlayerSyncSnapshot implements Serializable {
    public ItemStack[] inventory;
    public ItemStack[] armor;
    public ItemStack   offhand;
    public int level;
    public float exp;
    public double health;
    public int food;
    public float saturation;

    public static String toBase64(PlayerSyncSnapshot s) throws IOException {
        try (var bos = new ByteArrayOutputStream();
             var oos = new org.bukkit.util.io.BukkitObjectOutputStream(bos)) {
            oos.writeObject(s.inventory);
            oos.writeObject(s.armor);
            oos.writeObject(s.offhand);
            oos.writeInt(s.level);
            oos.writeFloat(s.exp);
            oos.writeDouble(s.health);
            oos.writeInt(s.food);
            oos.writeFloat(s.saturation);
            oos.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        }
    }

    @SuppressWarnings("unchecked")
    public static PlayerSyncSnapshot fromBase64(String base64) throws IOException, ClassNotFoundException {
        try (var bis = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             var ois = new org.bukkit.util.io.BukkitObjectInputStream(bis)) {
            PlayerSyncSnapshot s = new PlayerSyncSnapshot();
            s.inventory   = (ItemStack[]) ois.readObject();
            s.armor       = (ItemStack[]) ois.readObject();
            s.offhand     = (ItemStack)   ois.readObject();
            s.level       = ois.readInt();
            s.exp         = ois.readFloat();
            s.health      = ois.readDouble();
            s.food        = ois.readInt();
            s.saturation  = ois.readFloat();
            return s;
        }
    }
}
