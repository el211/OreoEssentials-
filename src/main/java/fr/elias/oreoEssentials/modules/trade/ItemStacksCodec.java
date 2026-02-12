package fr.elias.oreoEssentials.modules.trade;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public final class ItemStacksCodec {

    private ItemStacksCodec() {}

    public static byte[] encodeToBytes(ItemStack[] items) {
        if (items == null) items = new ItemStack[0];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
            out.writeInt(items.length);
            for (ItemStack it : items) out.writeObject(it);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public static ItemStack[] decodeFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new ItemStack[0];
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream in = new BukkitObjectInputStream(bais)) {
            int len = in.readInt();
            ItemStack[] arr = new ItemStack[len];
            for (int i = 0; i < len; i++) arr[i] = (ItemStack) in.readObject();
            return arr;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    public static String encodeBase64(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(encodeToBytes(items));
    }
    public static ItemStack[] decodeBase64(String b64) {
        if (b64 == null || b64.isEmpty()) return new ItemStack[0];
        return decodeFromBytes(Base64.getDecoder().decode(b64));
    }
}
