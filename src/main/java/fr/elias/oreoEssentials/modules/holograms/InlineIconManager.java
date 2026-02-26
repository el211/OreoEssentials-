package fr.elias.oreoEssentials.modules.holograms;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.*;


public final class InlineIconManager {

    private static final NamespacedKey K_IS_OREO = NamespacedKey.fromString("oreoessentials:oreo_hologram");
    private static final NamespacedKey K_PARENT  = NamespacedKey.fromString("oreoessentials:icon_parent");
    private static final NamespacedKey K_ICON    = NamespacedKey.fromString("oreoessentials:is_icon");


    private static final double LINE_HEIGHT = 0.27;

    private static final float ICON_RELATIVE_SCALE = 0.45f;

    private final String parentName;
    private final List<UUID> iconEntities = new ArrayList<>();

    public InlineIconManager(String parentName) {
        this.parentName = parentName.toLowerCase(Locale.ROOT);
    }


    public List<String> processAndSpawn(List<String> rawLines,
                                        Location baseLoc,
                                        double scale,
                                        OreoHologramBillboard billboard,
                                        int[] brightness) {
        despawnAll(baseLoc);

        if (baseLoc == null || baseLoc.getWorld() == null) return rawLines;

        List<String> cleaned = new ArrayList<>(rawLines.size());
        int totalLines = rawLines.size();

        for (int i = 0; i < totalLines; i++) {
            String line = rawLines.get(i);
            String iconMat = extractIconMaterial(line);

            if (iconMat != null) {
                cleaned.add(" ");
                double yOffset = computeYOffset(i, totalLines, scale);
                spawnIcon(baseLoc, yOffset, iconMat, scale, billboard, brightness);
            } else {
                cleaned.add(line);
            }
        }

        return cleaned;
    }


    public static String extractIconMaterial(String line) {
        if (line == null) return null;
        String stripped = stripFormattingCodes(line).trim();
        if (stripped.length() > 5
                && stripped.substring(0, 5).equalsIgnoreCase("ICON:")) {
            String mat = stripped.substring(5).trim();
            return mat.isEmpty() ? null : mat;
        }
        return null;
    }

    public void despawnAll(Location baseLoc) {
        for (UUID uuid : iconEntities) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                try { e.remove(); } catch (Throwable ignored) {}
            }
        }
        iconEntities.clear();

        if (baseLoc != null && baseLoc.getWorld() != null) {
            for (Entity e : baseLoc.getWorld().getNearbyEntities(baseLoc, 6, 6, 6,
                    ent -> ent instanceof ItemDisplay)) {
                PersistentDataContainer pdc = e.getPersistentDataContainer();
                if (pdc.has(K_ICON, PersistentDataType.BYTE)) {
                    String parent = pdc.get(K_PARENT, PersistentDataType.STRING);
                    if (parentName.equals(parent)) {
                        try { e.remove(); } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    public List<UUID> getIconEntityIds() {
        return Collections.unmodifiableList(iconEntities);
    }

    public static boolean hasAnyIcon(List<String> lines) {
        if (lines == null) return false;
        for (String l : lines) {
            if (extractIconMaterial(l) != null) return true;
        }
        return false;
    }


    private void spawnIcon(Location baseLoc,
                           double yOffset,
                           String materialName,
                           double holoScale,
                           OreoHologramBillboard billboard,
                           int[] brightness) {

        Location iconLoc = baseLoc.clone().add(0, yOffset, 0);
        World world = iconLoc.getWorld();
        if (world == null) return;

        ItemDisplay id = (ItemDisplay) world.spawnEntity(iconLoc, EntityType.ITEM_DISPLAY);

        PersistentDataContainer pdc = id.getPersistentDataContainer();
        pdc.set(K_IS_OREO, PersistentDataType.BYTE, (byte) 1);
        pdc.set(K_PARENT, PersistentDataType.STRING, parentName);
        pdc.set(K_ICON, PersistentDataType.BYTE, (byte) 1);

        Material mat = Material.matchMaterial(materialName);
        if (mat == null) {
            mat = Material.matchMaterial("minecraft:" + materialName.toLowerCase(Locale.ROOT));
        }
        if (mat == null) mat = Material.BARRIER;
        id.setItemStack(new ItemStack(mat));

        id.setBillboard(billboard.toNms());

        float iconScale = (float) (ICON_RELATIVE_SCALE * holoScale);
        Transformation tr = id.getTransformation();
        id.setTransformation(new Transformation(
                tr.getTranslation(),
                tr.getLeftRotation(),
                new Vector3f(iconScale, iconScale, iconScale),
                tr.getRightRotation()
        ));

        if (brightness != null && brightness.length == 2
                && (brightness[0] >= 0 || brightness[1] >= 0)) {
            int b = Math.max(0, Math.min(15, brightness[0]));
            int s = Math.max(0, Math.min(15, brightness[1]));
            id.setBrightness(new Display.Brightness(b, s));
        }

        id.setViewRange(64f);

        id.setShadowRadius(0f);
        id.setShadowStrength(0f);

        iconEntities.add(id.getUniqueId());
    }


    private static double computeYOffset(int lineIndex, int totalLines, double scale) {
        double lineH = LINE_HEIGHT * scale;
        return ((totalLines - 1) / 2.0 - lineIndex) * lineH;
    }


    private static String stripFormattingCodes(String s) {
        String out = s.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
        out = out.replaceAll("<[^>]+>", "");
        return out;
    }
}