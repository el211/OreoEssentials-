package fr.elias.oreoEssentials.modules.help;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HelpConfig {

    private final OreoEssentials plugin;
    private final File           configFile;
    private FileConfiguration    cfg;

    private boolean      simpleMode;
    private List<String> simpleText;
    private String       header;
    private String       footer;
    private int          entriesPerPage;
    private String       noPermissionMsg;
    private String       invalidPageMsg;
    private List<HelpEntry> entries;

    // -----------------------------------------------------------------------

    public HelpConfig(OreoEssentials plugin) {
        this.plugin     = plugin;
        this.configFile = new File(plugin.getDataFolder(), "server/help.yml");
        load();
    }

    // -----------------------------------------------------------------------

    public void load() {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("server/help.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(configFile);
        parse();
    }

    private void parse() {
        simpleMode      = "simple".equalsIgnoreCase(cfg.getString("help.mode", "paginated"));
        simpleText      = cfg.getStringList("help.simple-text");
        header          = cfg.getString("help.header",         "&8&m      &r &6&lServer Help &7(Page {page}/{total}) &8&m      ");
        footer          = cfg.getString("help.footer",         "&8&m      &r &7Use &e/help <page> &7to navigate &8&m      ");
        entriesPerPage  = Math.max(1, cfg.getInt("help.entries-per-page", 8));
        noPermissionMsg = cfg.getString("help.no-permission",  "&cYou don't have permission to use /help.");
        invalidPageMsg  = cfg.getString("help.invalid-page",   "&cInvalid page. There are &e{max} &cpage(s).");

        entries = new ArrayList<>();
        List<?> raw = cfg.getList("help.entries", Collections.emptyList());
        for (Object obj : raw) {
            if (!(obj instanceof ConfigurationSection sec)) {
                // MapList from YAML comes as LinkedHashMap — handle both
                if (obj instanceof java.util.Map<?,?> map) {
                    String command     = str(map, "command");
                    String description = str(map, "description");
                    String permission  = str(map, "permission");
                    if (command != null && !command.isEmpty()) {
                        entries.add(new HelpEntry(command, description != null ? description : "", permission));
                    }
                }
                continue;
            }
            String command     = sec.getString("command");
            String description = sec.getString("description", "");
            String permission  = sec.getString("permission", null);
            if (command != null && !command.isEmpty()) {
                entries.add(new HelpEntry(command, description, permission));
            }
        }
    }

    private static String str(java.util.Map<?,?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public boolean      isSimpleMode()    { return simpleMode; }
    public List<String> simpleText()     { return simpleText; }
    public String       header()         { return header; }
    public String       footer()         { return footer; }
    public int          entriesPerPage() { return entriesPerPage; }
    public String       noPermissionMsg(){ return noPermissionMsg; }
    public String       invalidPageMsg() { return invalidPageMsg; }

    /** Returns all entries, filtered to those the sender can see. */
    public List<HelpEntry> visibleEntries(org.bukkit.command.CommandSender sender) {
        List<HelpEntry> visible = new ArrayList<>();
        for (HelpEntry e : entries) {
            if (e.permission() == null || e.permission().isEmpty() || sender.hasPermission(e.permission())) {
                visible.add(e);
            }
        }
        return visible;
    }

    /** Total pages for a given sender (based on their visible entries). */
    public int totalPages(org.bukkit.command.CommandSender sender) {
        int size = visibleEntries(sender).size();
        return Math.max(1, (int) Math.ceil(size / (double) entriesPerPage));
    }

    /** Entries for a specific page (1-based). */
    public List<HelpEntry> page(org.bukkit.command.CommandSender sender, int page) {
        List<HelpEntry> all   = visibleEntries(sender);
        int start = (page - 1) * entriesPerPage;
        int end   = Math.min(start + entriesPerPage, all.size());
        if (start >= all.size()) return Collections.emptyList();
        return all.subList(start, end);
    }

    // -----------------------------------------------------------------------

    /** Immutable record for a single help entry. */
    public record HelpEntry(String command, String description, String permission) {}
}
