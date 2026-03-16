package fr.elias.oreoEssentials.modules.shop.models;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Per-shop GUI layout — controls every button in the navigation bar.
 * Loaded from the optional {@code gui:} section of each shop YAML.
 * When a shop has no {@code gui:} section the global ShopConfig defaults are used instead.
 */
public final class ShopGuiLayout {

    /**
     * Config for a single GUI button (filler, back, prev, next, page-indicator).
     */
    public static final class Button {
        private final boolean  enabled;
        private final int      slot;
        private final Material material;
        private final String   name;
        private final List<String> lore;
        private final int      modelData;

        public Button(boolean enabled, int slot, Material material,
                      String name, List<String> lore, int modelData) {
            this.enabled   = enabled;
            this.slot      = slot;
            this.material  = material;
            this.name      = name;
            this.lore      = lore;
            this.modelData = modelData;
        }

        public boolean      isEnabled()  { return enabled; }
        public int          getSlot()    { return slot; }
        public Material     getMaterial(){ return material; }
        public String       getName()    { return name; }
        public List<String> getLore()    { return lore; }
        public int          getModelData(){ return modelData; }
    }

    private final Button filler;
    private final Button back;
    private final Button prevPage;
    private final Button nextPage;
    private final Button pageIndicator;

    public ShopGuiLayout(Button filler, Button back, Button prevPage,
                         Button nextPage, Button pageIndicator) {
        this.filler        = filler;
        this.back          = back;
        this.prevPage      = prevPage;
        this.nextPage      = nextPage;
        this.pageIndicator = pageIndicator;
    }

    public Button getFiller()        { return filler; }
    public Button getBack()          { return back; }
    public Button getPrevPage()      { return prevPage; }
    public Button getNextPage()      { return nextPage; }
    public Button getPageIndicator() { return pageIndicator; }


    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses the {@code gui:} section of a shop YAML.
     * Any missing key falls back to the given defaults so old shop files
     * continue to work without any changes.
     *
     * @param sec           the {@code gui:} ConfigurationSection (may be null)
     * @param defaultBack   default slot for the back button  (from ShopConfig)
     * @param defaultPrev   default slot for prev-page button (from ShopConfig)
     * @param defaultNext   default slot for next-page button (from ShopConfig)
     * @param hideBackDefault whether the shop-level hide-back-button flag is set
     */
    public static ShopGuiLayout parse(ConfigurationSection sec,
                                      int defaultBack, int defaultPrev, int defaultNext,
                                      boolean hideBackDefault) {
        Button filler        = parseButton(sec, "filler",
                true, -1,
                Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), 0);

        Button back          = parseButton(sec, "back-button",
                !hideBackDefault, defaultBack,
                Material.ARROW, "&6&l\u2190 Back to Menu",
                List.of("&7Return to main shop menu"), 0);

        Button prevPage      = parseButton(sec, "prev-page",
                true, defaultPrev,
                Material.ARROW, "&e&l\u2190 Previous Page",
                List.of("&7Go to page {page}"), 0);

        Button nextPage      = parseButton(sec, "next-page",
                true, defaultNext,
                Material.ARROW, "&e&lNext Page \u2192",
                List.of("&7Go to page {page}"), 0);

        // default page-indicator slot = backSlot - 1  (same heuristic as old code)
        int defaultPgSlot = Math.max(0, defaultBack - 1);
        Button pageIndicator = parseButton(sec, "page-indicator",
                true, defaultPgSlot,
                Material.PAPER, "&f&lPage {page} &7of &f{total}",
                List.of("&7Use arrows to navigate pages"), 0);

        return new ShopGuiLayout(filler, back, prevPage, nextPage, pageIndicator);
    }

    private static Button parseButton(ConfigurationSection parent, String key,
                                       boolean defaultEnabled, int defaultSlot,
                                       Material defaultMat, String defaultName,
                                       List<String> defaultLore, int defaultCmd) {
        ConfigurationSection s = parent != null ? parent.getConfigurationSection(key) : null;

        boolean enabled = s != null ? s.getBoolean("enabled", defaultEnabled) : defaultEnabled;
        int     slot    = s != null ? s.getInt("slot", defaultSlot)           : defaultSlot;

        Material mat = defaultMat;
        if (s != null && s.isString("material")) {
            try { mat = Material.valueOf(s.getString("material", "").toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        String       name     = s != null ? s.getString("name", defaultName)   : defaultName;
        List<String> lore     = s != null && !s.getStringList("lore").isEmpty()
                                 ? s.getStringList("lore") : defaultLore;
        int          modelData = s != null ? s.getInt("model-data", defaultCmd) : defaultCmd;

        return new Button(enabled, slot, mat, name, lore, modelData);
    }
}
