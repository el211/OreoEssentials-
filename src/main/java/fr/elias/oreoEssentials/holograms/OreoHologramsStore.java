package fr.elias.oreoEssentials.holograms;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class OreoHologramsStore {
    private final File file;

    public OreoHologramsStore(File dataFolder) {
        this.file = new File(dataFolder, "oreoholograms.yml");
    }

    public List<OreoHologramData> loadAll() {
        if (!file.exists()) return List.of();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("holograms");
        if (root == null) return List.of();

        List<OreoHologramData> out = new ArrayList<>();
        for (String name : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(name);
            if (c == null) continue;

            OreoHologramData d = new OreoHologramData();
            d.name = name;
            d.type = OreoHologramType.valueOf(c.getString("type", "TEXT").toUpperCase());

            OreoHologramLocation loc = new OreoHologramLocation();
            loc.world = c.getString("world");
            loc.x = c.getDouble("x");
            loc.y = c.getDouble("y");
            loc.z = c.getDouble("z");
            loc.yaw = (float) c.getDouble("yaw");
            loc.pitch = (float) c.getDouble("pitch");
            d.location = loc;

            d.scale = c.getDouble("scale", 1.0);
            d.shadowStrength = c.getInt("shadowStrength", 0);
            d.shadowRadius = (float) c.getDouble("shadowRadius", 0.0);
            d.brightnessBlock = c.getInt("brightness.block", -1);
            d.brightnessSky = c.getInt("brightness.sky", -1);
            d.billboard = OreoHologramBillboard.from(c.getString("billboard", "CENTER"));
            d.visibilityDistance = c.getDouble("visibilityDistance", -1);
            d.visibility = OreoHologramVisibility.valueOf(c.getString("visibility", "ALL"));
            d.viewPermission = c.getString("viewPermission", "");
            d.manualViewers = c.getStringList("manualViewers");

            d.updateIntervalTicks = c.getLong("updateIntervalTicks",
                    c.getLong("updateTextIntervalTicks", 0L));

            if (d.type == OreoHologramType.TEXT) {
                d.lines = c.getStringList("lines");
                d.backgroundColor = c.getString("background", "TRANSPARENT");
                d.textShadow = c.getBoolean("textShadow", false);
                d.textAlign = c.getString("textAlignment", "CENTER");
            } else if (d.type == OreoHologramType.ITEM) {
                d.itemStackBase64 = c.getString("item", "");
            } else if (d.type == OreoHologramType.BLOCK) {
                d.blockType = c.getString("block", "STONE");
            }

            out.add(d);
        }
        return out;
    }

    public void saveAll(List<OreoHologramData> list) {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("holograms");

        for (OreoHologramData d : list) {
            ConfigurationSection c = root.createSection(d.name);
            c.set("type", d.type.name());
            c.set("world", d.location.world);
            c.set("x", d.location.x);
            c.set("y", d.location.y);
            c.set("z", d.location.z);
            c.set("yaw", d.location.yaw);
            c.set("pitch", d.location.pitch);

            c.set("scale", d.scale);
            c.set("shadowStrength", d.shadowStrength);
            c.set("shadowRadius", d.shadowRadius);
            if (d.brightnessBlock >= 0 || d.brightnessSky >= 0) {
                c.set("brightness.block", d.brightnessBlock);
                c.set("brightness.sky", d.brightnessSky);
            }
            c.set("billboard", d.billboard.name());
            c.set("visibilityDistance", d.visibilityDistance);
            c.set("visibility", d.visibility.name());
            c.set("viewPermission", d.viewPermission);
            c.set("manualViewers", d.manualViewers);

            c.set("updateIntervalTicks", d.updateIntervalTicks);

            switch (d.type) {
                case TEXT -> {
                    c.set("lines", d.lines);
                    c.set("background", d.backgroundColor);
                    c.set("textShadow", d.textShadow);
                    c.set("textAlignment", d.textAlign);
                    // legacy compatibility for older configs
                    c.set("updateTextIntervalTicks", d.updateIntervalTicks);
                }
                case ITEM -> c.set("item", d.itemStackBase64);
                case BLOCK -> c.set("block", d.blockType);
            }
        }

        try {
            y.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
