package fr.elias.oreoEssentials.modules.enderchest;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface EnderChestStorage {

    ItemStack[] load(UUID playerId, int rows);


    void save(UUID playerId, int rows, ItemStack[] contents);


    static String serialize(ItemStack[] contents) throws Exception {
        try (var baos = new java.io.ByteArrayOutputStream();
             var oos  = new org.bukkit.util.io.BukkitObjectOutputStream(baos)) {
            int len = contents == null ? 0 : contents.length;
            oos.writeInt(len);
            for (int i = 0; i < len; i++) {
                oos.writeObject(contents[i]);
            }
            oos.flush();
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    static ItemStack[] deserialize(String base64) throws Exception {
        if (base64 == null || base64.isEmpty()) return null;
        byte[] data = java.util.Base64.getDecoder().decode(base64);
        try (var bais = new java.io.ByteArrayInputStream(data);
             var ois  = new org.bukkit.util.io.BukkitObjectInputStream(bais)) {
            int len = ois.readInt();
            ItemStack[] out = new ItemStack[len];
            for (int i = 0; i < len; i++) {
                out[i] = (ItemStack) ois.readObject();
            }
            return out;
        }
    }

    static ItemStack[] clamp(ItemStack[] src, int rows) {
        int size = Math.max(1, rows) * 9;
        if (src == null) return null;

        if (src.length >= size) {
            return src;
        }

        ItemStack[] dst = new ItemStack[size];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

}
