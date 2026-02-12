// File: src/main/java/fr/elias/oreoEssentials/kits/KitsManager.java
package fr.elias.oreoEssentials.modules.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class KitsManager {

    private final OreoEssentials plugin;

    private File kitsFile;
    private FileConfiguration kitsCfg;
    private File dataFile;
    private FileConfiguration dataCfg;

    // Menu settings
    private boolean useItemsAdder;
    private String menuTitle;
    private int menuRows;
    private boolean menuFill;
    private String fillMaterial;

    // Runtime toggle
    private volatile boolean enabled = true;

    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitsManager(OreoEssentials plugin) {
        this.plugin = plugin;
        loadFiles();
        reload();
    }



    private void loadFiles() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        // Ensure default kits.yml is written from JAR
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            try {
                plugin.saveResource("kits.yml", false);
                plugin.getLogger().info("[Kits] Wrote default kits.yml");
            } catch (IllegalArgumentException iae) {
                plugin.getLogger().severe("[Kits] kits.yml is missing from the JAR (saveResource failed).");
                plugin.getLogger().severe("[Kits] Place it at src/main/resources/kits.yml (jar root).");
            }
        }

        dataFile = new File(plugin.getDataFolder(), "kitsdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }

        kitsCfg = YamlConfiguration.loadConfiguration(kitsFile);
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void reload() {
        try { kitsCfg.load(kitsFile); } catch (Exception e) {
            plugin.getLogger().severe("[Kits] Failed to load kits.yml: " + e.getMessage());
        }
        try { dataCfg.load(dataFile); } catch (Exception e) {
            plugin.getLogger().severe("[Kits] Failed to load kitsdata.yml: " + e.getMessage());
        }

        enabled = kitsCfg.getBoolean("settings.enable", true);

        this.useItemsAdder = kitsCfg.getBoolean("use-itemadder", true) && ItemParser.isItemsAdderPresent();

        ConfigurationSection menuSec = kitsCfg.getConfigurationSection("menu");
        this.menuTitle    = ItemParser.color(menuSec != null ? menuSec.getString("title", "&6Kits") : "&6Kits");
        this.menuRows     = Math.max(1, Math.min(6, menuSec != null ? menuSec.getInt("rows", 3) : 3));
        this.menuFill     = menuSec != null && menuSec.getBoolean("fill", true);
        this.fillMaterial = menuSec != null ? menuSec.getString("fill-material", "GRAY_STAINED_GLASS_PANE") : "GRAY_STAINED_GLASS_PANE";

        kits.clear();
        ConfigurationSection sec = kitsCfg.getConfigurationSection("kits");
        if (sec == null) {
            plugin.getLogger().warning("[Kits] No 'kits' section found in kits.yml.");
            return;
        }

        int loaded = 0, broken = 0;
        for (String id : sec.getKeys(false)) {
            try {
                ConfigurationSection k = sec.getConfigurationSection(id);
                if (k == null) { broken++; continue; }

                String display = ItemParser.color(k.getString("display-name", id));

                String iconDef = k.getString("icon", "CHEST");
                ItemStack icon = ItemParser.parseItem(iconDef, useItemsAdder);
                if (icon == null) {
                    plugin.getLogger().warning("[Kits] Kit '" + id + "': invalid icon '" + iconDef + "'. Using CHEST.");
                    icon = new ItemStack(org.bukkit.Material.CHEST);
                }

                long cooldown = k.getLong("cooldown-seconds", 0L);
                Integer slot = k.isInt("slot") ? k.getInt("slot") : null;

                List<ItemStack> items = new ArrayList<>();
                for (String line : k.getStringList("items")) {
                    ItemStack it = ItemParser.parseItem(line, useItemsAdder);
                    if (it == null) {
                        plugin.getLogger().warning("[Kits] Kit '" + id + "': invalid item '" + line + "'. Skipping.");
                        continue;
                    }
                    items.add(it);
                }

                List<String> commands = k.getStringList("commands");
                if (commands != null && commands.isEmpty()) commands = null;

                kits.put(id.toLowerCase(Locale.ROOT),
                        new Kit(id, display, icon, items, cooldown, slot, commands));
                loaded++;
            } catch (Throwable t) {
                plugin.getLogger().severe("[Kits] Failed to load kit '" + id + "': " + t.getMessage());
                broken++;
            }
        }
        plugin.getLogger().info("[Kits] Loaded " + loaded + " kit(s). Broken: " + broken + ".");
    }



    public Map<String, Kit> getKits() { return Collections.unmodifiableMap(kits); }
    public String getMenuTitle() { return menuTitle; }
    public int getMenuRows() { return menuRows; }
    public boolean isMenuFill() { return menuFill; }
    public String getFillMaterial() { return fillMaterial; }

    /* ============================================================
     * Enable / Disable Toggle
     * ============================================================ */

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean v) {
        enabled = v;
        persistToggle();
    }

    public boolean toggleEnabled() {
        setEnabled(!enabled);
        return enabled;
    }

    private void persistToggle() {
        try {
            kitsCfg.set("settings.enable", enabled);
            kitsCfg.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Kits] Failed to persist settings.enable: " + e.getMessage());
        }
    }



    public long getSecondsLeft(Player p, Kit kit) {
        if (p.hasPermission("oreo.kit.bypasscooldown")) return 0;
        if (kit.getCooldownSeconds() <= 0) return 0;

        long last = dataCfg.getLong("players." + p.getUniqueId() + "." + kit.getId(), 0);
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long readyAt = last + kit.getCooldownSeconds();
        long left = readyAt - now;
        return Math.max(0, left);
    }

    public void markClaim(Player p, Kit kit) {
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        dataCfg.set("players." + p.getUniqueId() + "." + kit.getId(), now);
        saveData();
    }

    public void saveData() {
        try { dataCfg.save(dataFile); } catch (Exception ignored) {}
    }


    public boolean claim(Player p, String kitId) {
        Kit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) {
            Lang.send(p, "kits.unknown-kit",
                    "<red>Unknown kit: <yellow>%kit_id%</yellow></red>",
                    Map.of("kit_id", kitId));
            return false;
        }

        // Gate when disabled
        if (!isEnabled()) {
            Lang.send(p, "kits.disabled",
                    "<red>Kits are currently disabled.</red>",
                    Map.of());
            if (p.hasPermission("oreo.kits.admin")) {
                Lang.send(p, "kits.disabled.hint",
                        "<gray>Use <white>%cmd%</white> to enable it.</gray>",
                        Map.of("cmd", "/kits toggle"));
            }
            return true;
        }

        if (!p.hasPermission("oreo.kit.claim")) {
            Lang.send(p, "kits.no-permission-claim",
                    "<red>You don't have permission to claim kits.</red>",
                    Map.of());
            return true;
        }

        String kitPerm = "oreo.kit." + kit.getId().toLowerCase(Locale.ROOT);

        if (!p.hasPermission(kitPerm)
                && !p.hasPermission("oreo.kit.*")
                && !p.hasPermission("oreo.kits.admin")) {

            Lang.send(p, "kits.no-permission-specific",
                    "<red>You don't have permission for kit <yellow>%kit_name%</yellow>. Need: <white>%perm%</white></red>",
                    Map.of(
                            "kit_id", kit.getId(),
                            "kit_name", kit.getDisplayName(),
                            "perm", kitPerm
                    ));
            return true;
        }

        long left = getSecondsLeft(p, kit);
        if (left > 0 && !p.hasPermission("oreo.kit.bypasscooldown")) {
            Lang.send(p, "kits.cooldown",
                    "<yellow>Kit <aqua>%kit_name%</aqua> is on cooldown. Wait <white>%cooldown_left%</white>.</yellow>",
                    Map.of(
                            "kit_name", kit.getDisplayName(),
                            "cooldown_left", Lang.timeHuman(left),
                            "cooldown_left_raw", String.valueOf(left)
                    ));
            return true;
        } else if (left > 0) {
            Lang.send(p, "kits.bypass-cooldown",
                    "<green>Bypassing cooldown for <aqua>%kit_name%</aqua>.</green>",
                    Map.of("kit_name", kit.getDisplayName()));
        }

        for (ItemStack it : kit.getItems()) {
            if (it == null) continue;
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(it.clone());
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        }

        if (kit.getCommands() != null) {
            long delayTicks = 0L;
            for (String raw : kit.getCommands()) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty()) continue;

                String lower = line.toLowerCase(Locale.ROOT);

                if (lower.startsWith("delay!")) {
                    delayTicks += parseDelayTicks(line);
                    continue;
                }

                long runAt = delayTicks;
                Bukkit.getScheduler().runTaskLater(plugin, () -> runKitCommand(p, line), runAt);
            }

            Lang.send(p, "kits.commands-ran",
                    "<gray>Commands from <aqua>%kit_name%</aqua> executed.</gray>",
                    Map.of("kit_name", kit.getDisplayName()));
        }

        markClaim(p, kit);
        Lang.send(p, "kits.claimed",
                "<green>You received the <aqua>%kit_name%</aqua> kit!</green>",
                Map.of("kit_name", kit.getDisplayName()));
        return true;
    }

    private long parseDelayTicks(String line) {
        try {
            String arg = line.substring("delay!".length()).trim().toLowerCase(Locale.ROOT);
            if (arg.isEmpty()) return 0L;

            if (arg.endsWith("t")) {
                arg = arg.substring(0, arg.length() - 1).trim();
                if (arg.isEmpty()) return 0L;
                return Math.max(0L, Long.parseLong(arg));
            }

            if (arg.endsWith("s")) {
                arg = arg.substring(0, arg.length() - 1).trim();
            } else if (arg.endsWith("sec")) {
                arg = arg.substring(0, arg.length() - 3).trim();
            }

            if (arg.isEmpty()) return 0L;

            double seconds = Double.parseDouble(arg);
            return Math.max(0L, (long) Math.round(seconds * 20.0));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void runKitCommand(Player p, String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) return;

        String withPlayer = line.replace("%player%", p.getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            withPlayer = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, withPlayer);
        }
        String lower = withPlayer.toLowerCase(Locale.ROOT);

        if (lower.startsWith("console:")) {
            String cmd = withPlayer.substring("console:".length()).trim();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }
        if (lower.startsWith("player:")) {
            String cmd = withPlayer.substring("player:".length()).trim();
            Bukkit.dispatchCommand(p, cmd);
            return;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), withPlayer);
    }

    public FileConfiguration kitsCfg() { return kitsCfg; }
    public OreoEssentials getPlugin() { return plugin; }
}